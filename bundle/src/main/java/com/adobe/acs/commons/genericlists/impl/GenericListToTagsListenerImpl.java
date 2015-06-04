/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2015 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.adobe.acs.commons.genericlists.impl;

import com.adobe.acs.commons.genericlists.GenericList;
import com.adobe.cq.testing.client.TagClient;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagConstants;
import com.day.cq.tagging.TagManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Component(
        label = "ACS AEM Commons - Generic Lists to Tags",
        description = "Converts Generic Lists to Tags",
        immediate = true
)
@Properties({
        @Property(
                label = "Event Topics",
                value = { SlingConstants.TOPIC_RESOURCE_CHANGED },
                description = "[Required] Event Topics this event handler will to respond to.",
                name = EventConstants.EVENT_TOPIC,
                propertyPrivate = true
        ),
        @Property(
                name = JobConsumer.PROPERTY_TOPICS,
                value = GenericListToTagsListenerImpl.JOB_TOPIC,
                propertyPrivate = true
        )
})
@Service
public class GenericListToTagsListenerImpl implements EventHandler, TopologyEventListener, JobConsumer {
    private static final Logger log = LoggerFactory.getLogger(GenericListToTagsListenerImpl.class);

    public static final String PATH_TAGS_ROOT = "/etc/tags/";
    
    public static final String JOB_TOPIC = "com/adobe/acs/commons/generic-list-to-tags";
    
    private static final String PN_GENERATE_TAGS = "generateTags";

    private static final String PN_TAG_NAMESPACE = "tagNamespace";

    private static final String PN_SLING_RESOURCE_TYPE = "sling:resourceType";

    private static final String PN_TAG_NAMESPACE_TITLE = "tagNamespaceTitle";

    private static final String RT_TAG = "cq/tagging/components/tag";
    
    private boolean isLeader = false;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;
    
    @Reference
    private JobManager jobManager;

    @Override
    public void handleEvent(final Event event) {
        if (!this.isLeader) {
            return;
        }
        
        final String path = (String) event.getProperty(SlingConstants.PROPERTY_PATH);
        
        if(!StringUtils.endsWith(path, JcrConstants.JCR_CONTENT)) {
            return;    
        }
        
        log.debug("Event path: {}", path);
        
        ResourceResolver resourceResolver = null;
        
        try {
            resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            
            final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            final Resource resource = resourceResolver.getResource(path);
            final Page page = pageManager.getContainingPage(resource);
            
            final GenericList list = page.adaptTo(GenericList.class);
            
            if(list == null) {
                // Page is not a Generic List page
                log.debug("Was not generic list page");
                return;
            }
            
            final ValueMap properties = resource.adaptTo(ValueMap.class);
            if (!properties.get(PN_GENERATE_TAGS, false)){
                return;
            }

            final Map<String, Object> payload = new HashMap<String, Object>();
            payload.put(SlingConstants.PROPERTY_PATH, path);
            
            log.debug("Kicking off job for: {}", path);

            jobManager.addJob(JOB_TOPIC, payload);

        } catch (LoginException e) {
            log.error("Could not handle event to convert Generic List to Tags", e);
        }
    }

    @Override
    public JobResult process(final Job job) {
        String path = (String) job.getProperty(SlingConstants.PROPERTY_PATH);

        ResourceResolver resourceResolver = null;

        try {
            resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
            
            final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            final TagManager tagManager = resourceResolver.adaptTo(TagManager.class);
            
            /* Data */

            final Resource resource = resourceResolver.getResource(path);
            if(resource == null) {
                // Resource is gone!
                return JobResult.CANCEL;
            }

            final Page page = pageManager.getContainingPage(resource);
            final ValueMap properties = page.getProperties();
            final GenericList list = page.adaptTo(GenericList.class);

            /* Handle Getting or Creating the Namespace */

            final String namespaceTitle = StringUtils.defaultIfEmpty(
                    properties.get(PN_TAG_NAMESPACE_TITLE, page.getTitle()), page.getName());
            final String namespace = properties.get(PN_TAG_NAMESPACE, page.getName());

            Tag namespaceTag = null;
            for(final Tag tmp : tagManager.getNamespaces()) {
                if(StringUtils.equals(tmp.getTagID(), namespace + TagConstants.NAMESPACE_DELIMITER)) {
                    namespaceTag = tmp;
                }
            }

            log.debug("Processing namespace [ {} - {} ]", namespaceTitle, namespace);

            if (namespaceTag == null) {
                // Creating namespace via Node APIs since the TagManager API createsTags under the default namespace.
                
                final Node node = JcrUtils.getOrCreateByPath(PATH_TAGS_ROOT + namespace, TagConstants.NT_TAG, resourceResolver.adaptTo(Session.class));
                node.setProperty(JcrConstants.JCR_TITLE, StringUtils.defaultIfEmpty(namespaceTitle, page.getName()));
                node.setProperty(JcrConstants.JCR_DESCRIPTION, page.getDescription());
                node.setProperty(PN_SLING_RESOURCE_TYPE, RT_TAG);

                namespaceTag = tagManager.resolve(node.getPath());
                
                log.debug("Created namespace Tag: {}", namespaceTag.getTagID());
            } else {
                setTitle(resourceResolver, namespaceTitle, namespaceTag);
                
                log.debug("Found existing namespace Tag: {}", namespaceTag.getTagID());
            }

            /* Create new Tags or update existing Tags */
            final Set<String> validTags = new HashSet<String>();

            log.debug("Found [ {} ] Generic List Items to process", list.getItems().size());
            
            for(final GenericList.Item item : list.getItems()) {
                log.debug("Processing Generic List Item [ {} - {} ]", item.getTitle(), item.getValue());
                final String tagId = namespace + TagConstants.NAMESPACE_DELIMITER + item.getValue();
                
                Tag tag = tagManager.resolve(tagId);
                
                if(tag == null) {
                    // New Tag
                    tag = tagManager.createTag(tagId, item.getTitle(), "");
                    log.debug("Created new Tag: {}", tag.getTagID());
                } else if(StringUtils.equals(tag.getTitle(), item.getTitle())) {
                    // Update Tag's Title
                    setTitle(resourceResolver, item.getTitle(), tag);
                    log.debug("Updated existing Tag: {}", tag.getTagID());
                }

                // Record this as a valid tag to keeps
                validTags.add(tag.getTagID());
            }


            final Iterator<Tag> existingTags = namespaceTag.listChildren();

            /* Remove any tags that should be removed */
            
            while(existingTags.hasNext()) {
                final Tag tag = existingTags.next();
                
                if(!validTags.contains(tag.getTagID())) {
                    final Resource resourceToDelete = resourceResolver.getResource(tag.getPath());
                    final Node nodeToDelete = resourceToDelete.adaptTo(Node.class);
                    
                    log.debug("Removed existing Tag: {}", tag.getTagID());

                    nodeToDelete.remove();
                }
            }
            
            /* Save changes; Any exception thrown will prevent the entire Tag up from being committed */
            if(resourceResolver.hasChanges()) {
                resourceResolver.commit();
                log.debug("Saved Generic List to Tags changes.");
            }
        } catch (Exception e) {
            log.error("Could not handle job to convert Generic List to Tags", e);
            return JobResult.FAILED;
        }

        return JobConsumer.JobResult.OK;
    }

    private void setTitle(ResourceResolver resourceResolver, String title, Tag tag) {
        final Resource tagResource = resourceResolver.getResource(tag.getPath());
        final ModifiableValueMap tagProperties = tagResource.adaptTo(ModifiableValueMap.class);

        if (!StringUtils.equals(title, tagProperties.get(JcrConstants.JCR_TITLE, ""))) {
            tagProperties.put(JcrConstants.JCR_TITLE, title);
        }
    }

    @Override
    public void handleTopologyEvent(final TopologyEvent event) {
        if (event.getType() == TopologyEvent.Type.TOPOLOGY_CHANGED
                || event.getType() == TopologyEvent.Type.TOPOLOGY_INIT) {
            this.isLeader = event.getNewView().getLocalInstance().isLeader();
        }
    }
}