package com.adobe.acs.commons.remoteassets.impl.packages;

import com.adobe.acs.commons.remoteassets.SyncStateManager;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;

import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Service
public class SyncStateManagerImpl implements SyncStateManager {

    private ConcurrentHashMap<String, Calendar> map = new ConcurrentHashMap<String, Calendar>();

    public void add(String path) {
        map.put(path, Calendar.getInstance());
    }

    public boolean contains(String path) {
        return map.containsKey(path);
    }

    public void remove(String path) {
        map.remove(path);
    }

}
