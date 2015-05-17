package com.adobe.acs.commons.workflow.audit.impl;


public interface Constants {

    String NT_SLING_FOLDER = "sling:Folder";

    String NT_CQ_WORKFLOW = "cq:Workflow";

    String NT_SLING_ORDERED_FOLDER = "sling:OrderedFolder";

    String PATH_WORKFLOW_AUDIT = "/etc/workflow/audit";

    String PATH_WORKFLOW_INSTANCES = "/etc/workflow/instances";

    String PN_CONTAINEE_INSTANCE_ID = "containeeInstanceId";

    String PN_PARENT_INSTANCE_ID = "parentInstanceId";

    String PN_IS_CONTAINER = "isContainer";

    String PN_CONTAINEE_AUDIT_PATH = "containeeAuditPath";

    String PN_CREATED_AT = "createdAt";

    String PN_CONTAINEE_INSTANCE_IDS = "containeeInstanceIds";

    String PN_IS_HARVESTED = "harvested";

    String RT_CQ_WORKITEM = "cq/workflow/components/workitem";

    String RT_CQ_WORKFLOW_INSTANCE = "cq/workflow/components/instance";

    String RT_WORKFLOW_INSTANCE_AUDIT = "acs-commons/components/utilities/workflow-audit/workflow-instance";

    String RT_WORKFLOW_ITEM_AUDIT = "acs-commons/components/utilities/workflow-audit/workflow-item";

    String PN_IS_CONTAINEE = "containee";

    String PN_ORIGIN_INSTANCE_ID = "originInstanceId";
}
