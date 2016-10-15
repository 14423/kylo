/**
 * 
 */
package com.thinkbiganalytics.nifi.v2.core.metadata;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.thinkbiganalytics.metadata.rest.client.MetadataClient;
import com.thinkbiganalytics.metadata.rest.model.feed.Feed;
import com.thinkbiganalytics.nifi.core.api.metadata.MetadataConstants;
import com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder;
import com.thinkbiganalytics.nifi.core.api.metadata.WaterMarkActiveException;

/**
 *
 * @author Sean Felten
 */
public class MetadataClientRecorder implements MetadataRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(MetadataClientRecorder.class);
    
    private static final String CURRENT_WATER_MARKS_ATTR = "activeWaterMarks";
    private static final ObjectReader WATER_MARKS_READER = new ObjectMapper().reader().forType(Map.class);
    private static final ObjectWriter WATER_MARKS_WRITER = new ObjectMapper().writer().forType(Map.class);
    
    private MetadataClient client;
    private Set<String> activeWaterMarks = Collections.synchronizedSet(new HashSet<>());
    
    // TODO: Remove this
    public Map<String, Boolean> workaroundRegistration = new HashMap<>();

    
    public MetadataClientRecorder() {
        this(URI.create("http://localhost:8420/api/metadata"));
    }
    
    public MetadataClientRecorder(URI baseUri) {
        this(new MetadataClient(baseUri));
    }
    
    public MetadataClientRecorder(MetadataClient client) {
        this.client = client;
    }
    
    

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#loadWaterMark(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String, java.lang.String)
     */
    @Override
    public FlowFile loadWaterMark(ProcessSession session, FlowFile ff, String feedId, String waterMarkName, String parameterName, String defaultValue) throws WaterMarkActiveException {
        recordActiveWaterMark(feedId, waterMarkName);
        
        String value = getHighWaterMarkValue(feedId, waterMarkName).orElse(defaultValue);
        FlowFile resultFF = addCurrentWaterMarksAttr(session, ff, waterMarkName, parameterName);
        resultFF = session.putAttribute(resultFF, parameterName, value);
        return session.putAttribute(resultFF, initValueParameterName(parameterName), value);
    }
    
    private void recordActiveWaterMark(String feedId, String waterMarkName) throws WaterMarkActiveException {
        String feedWaterMarkName = asFeedWaterMarkName(feedId, waterMarkName);
        boolean added = this.activeWaterMarks.add(feedWaterMarkName);
        
        if (! added) {
            throw new WaterMarkActiveException(waterMarkName);
        }
    }
    
    private boolean releaseActiveWaterMark(String feedId, String waterMarkName) {
        return this.activeWaterMarks.remove(asFeedWaterMarkName(feedId, waterMarkName));
    }
    
    private String asFeedWaterMarkName(String feedId, String waterMarkName) {
        return feedId + "." + waterMarkName;
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#recordWaterMark(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String, java.lang.String, java.io.Serializable)
     */
    @Override
    public FlowFile recordWaterMark(ProcessSession session, FlowFile ff, String feedId, String waterMarkName, String parameterName, String newValue) {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        
        if (actives.containsKey(waterMarkName)) {
            return session.putAttribute(ff, parameterName, newValue);
        } else {
            throw new IllegalStateException("No active high-water mark named \"" + waterMarkName + "\"");
        }
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#commitWaterMark(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String, java.lang.String)
     */
    @Override
    public FlowFile commitWaterMark(ProcessSession session, FlowFile ff, String feedId, String waterMarkName) {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        FlowFile resultFF = ff;
        
        if (actives.containsKey(waterMarkName)) {
            String parameterName = actives.remove(waterMarkName);
            String value = ff.getAttribute(parameterName);
            
            updateHighWaterMarkValue(feedId, waterMarkName, value);
            resultFF = setCurrentWaterMarksAttr(session, resultFF, actives);
            releaseActiveWaterMark(feedId, waterMarkName);
            return resultFF;
        } else {
            throw new IllegalStateException("No active high-water mark named \"" + waterMarkName + "\"");
        }
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#commitAllWaterMarks(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String)
     */
    @Override
    public FlowFile commitAllWaterMarks(ProcessSession session, FlowFile ff, String feedId) {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        FlowFile resultFF = ff;
        
        // TODO do more efficiently
        for (String waterMarkName : new HashSet<String>(actives.keySet())) {
            resultFF = commitWaterMark(session, resultFF, feedId, waterMarkName);
        }
        
        return resultFF;
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#releaseWaterMark(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String, java.lang.String)
     */
    @Override
    public FlowFile releaseWaterMark(ProcessSession session, FlowFile ff, String feedId, String waterMarkName) {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        FlowFile resultFF = ff;
        
        if (actives.containsKey(waterMarkName)) {
            String parameterName = actives.remove(waterMarkName);
            String value = getHighWaterMarkValue(feedId, waterMarkName)
                            .orElse(ff.getAttribute(initValueParameterName(parameterName)));
            
            resultFF = session.putAttribute(resultFF, parameterName, value);
            resultFF = setCurrentWaterMarksAttr(session, resultFF, actives);
            releaseActiveWaterMark(feedId, waterMarkName);
            return resultFF;
        } else {
            throw new IllegalStateException("No active high-water mark named \"" + waterMarkName + "\"");
        }
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.nifi.core.api.metadata.MetadataRecorder#releaseAllWaterMarks(org.apache.nifi.processor.ProcessSession, org.apache.nifi.flowfile.FlowFile, java.lang.String)
     */
    @Override
    public FlowFile releaseAllWaterMarks(ProcessSession session, FlowFile ff, String feedId) {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        FlowFile resultFF = ff;
        
        for (String waterMarkName : new HashSet<String>(actives.keySet())) {
            resultFF = releaseWaterMark(session, resultFF, feedId, waterMarkName);
        }
        
        return resultFF;
    }


    
    @Override
    public boolean isFeedInitialized(FlowFile ff) {
        String feedId = ff.getAttribute(MetadataConstants.FEED_ID_PROP);
        
        if (feedId != null) {
            Feed feed = this.client.getFeed(feedId);
            
            if (feed != null) {
                return feed.isInitialized();
            } else {
                log.info("Could not confirm feed initialization - no feed exists with ID: {}", feedId);
                return false;
            } 
        } else {
            log.info("Could not confirm feed initialization - no feed ID in flow file", feedId);
            return false;
        } 
    }

    @Override
    public void recordFeedInitialization(ProcessSession session, FlowFile ff, boolean flag) {
        String feedId = ff.getAttribute(MetadataConstants.FEED_ID_PROP);
        
        if (feedId != null) {
            Feed feed = this.client.getFeed(feedId);
            
            if (feed != null) {
                feed.setInitialized(flag);
                this.client.updateFeed(feed);
            }
        }
    }



    @Override
    public void updateFeedStatus(ProcessSession session, FlowFile ff, String statusMsg) {
        // TODO Auto-generated method stub
        
    }

    @Override
    // TODO: Remove workaroundRegistration
    public void recordFeedInitialization(String systemCategory, String feedName) {
        String key = feedKey(systemCategory, feedName);
        log.warn("recordFeedInit feed {} size {}", key, workaroundRegistration.size());
        workaroundRegistration.put(key, true);
    }

    @Override
    // TODO: Remove workaroundRegistration
    public boolean isFeedInitialized(String systemCategory, String feedName) {
        String key = feedKey(systemCategory,feedName);
        Boolean result = workaroundRegistration.get(key);
        log.warn("isFeedInitialized feed {} size {} result {}", key, workaroundRegistration.size(), result);
        return (result == null ? false : result);
    }

//    @Override
//    // TODO: Remove workaroundwatermark
//    public void recordLastLoadTime(String systemCategory, String feedName, DateTime time) {
//        String key = feedKey(systemCategory,feedName);
//        workaroundWatermark.put(key, time);
//    }
//
//    @Override
//    // TODO: Remove workaroundwatermark
//    public DateTime getLastLoadTime(String systemCategory, String feedName) {
//        String key = feedKey(systemCategory,feedName);
//        DateTime dt =  workaroundWatermark.get(key);
//        return (dt == null ? new DateTime(0L) : dt);
//    }

    private Map<String, String> getCurrentWaterMarksAttr(FlowFile ff) {
        try {
            String activeStr = ff.getAttribute(CURRENT_WATER_MARKS_ATTR);
            
            if (activeStr == null) {
                return new HashMap<>();
            } else {
                return WATER_MARKS_READER.readValue(activeStr);
            }
        } catch (Exception e) {
            // Should never happen.
            throw new IllegalStateException(e);
        }
    }
    
    private FlowFile addCurrentWaterMarksAttr(ProcessSession session, FlowFile ff, String waterMarkName, String parameterName) throws WaterMarkActiveException {
        Map<String, String> actives = getCurrentWaterMarksAttr(ff);
        
        actives.put(waterMarkName, parameterName);
        return setCurrentWaterMarksAttr(session, ff, actives);
    }

    private FlowFile setCurrentWaterMarksAttr(ProcessSession session, FlowFile ff, Map<String, String> actives) {
        try {
            return session.putAttribute(ff, CURRENT_WATER_MARKS_ATTR, WATER_MARKS_WRITER.writeValueAsString(actives));
        } catch (Exception e) {
            // Should never happen.
            throw new IllegalStateException(e);
        }
    }

    private Optional<String> getHighWaterMarkValue(String feedId, String waterMarkName) {
        return this.client.getHighWaterMarkValue(feedId, waterMarkName);
    }

    private void updateHighWaterMarkValue(String feedId, String waterMarkName, String value) {
        this.client.updateHighWaterMarkValue(feedId, waterMarkName, value);
    }

    private String initValueParameterName(String parameterName) {
        return parameterName + ".original";
    }

    // TODO: Remove workaround
    private String feedKey(String systemCategory, String feedName) {
        return systemCategory+"."+feedName;
    }

}
