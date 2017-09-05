package com.thinkbiganalytics.metadata.sla.api.core;

/*-
 * #%L
 * thinkbig-sla-metrics-default
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.thinkbiganalytics.metadata.api.MetadataAccess;
import com.thinkbiganalytics.metadata.api.event.MetadataEventListener;
import com.thinkbiganalytics.metadata.api.event.MetadataEventService;
import com.thinkbiganalytics.metadata.api.event.feed.FeedOperationStatusEvent;
import com.thinkbiganalytics.metadata.api.jobrepo.job.BatchJobExecution;
import com.thinkbiganalytics.metadata.api.jobrepo.job.BatchJobExecutionProvider;
import com.thinkbiganalytics.metadata.api.op.FeedOperation;

import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

/**
 * Service to listen for feed failure events and notify listeners when a feed fails
 */
@Component
public class FeedFailureService {


    @Inject
    private MetadataEventService eventService;

    @Inject
    private BatchJobExecutionProvider batchJobExecutionProvider;

    @Inject
    private MetadataAccess metadataAccess;

    public DateTime initializeTime = new DateTime();
    /**
     * Map with the Latest recorded Feed Failure
     */
    private Map<String, LastFeedFailure> lastFeedFailureMap = new HashMap<>();

    public static LastFeedFailure EMPTY_JOB = new LastFeedFailure("empty",0L,DateTime.now(),true);

    /**
     * Map with the Latest recorded failure that has been assessed by the FeedFailureMetricAssessor
     */
    private Map<String, LastFeedFailure> lastAssessedFeedFailureMap = new HashMap<>();


    public LastFeedFailure getLastFeedFailure(String feedName){
        return lastFeedFailureMap.get(feedName);
    }

    public LastFeedFailure findLastJob(String feedName){
     return   metadataAccess.read(() -> {
     BatchJobExecution latestJob = batchJobExecutionProvider.findLatestFinishedJobForFeed(feedName);
         if(latestJob != null) {
             LastFeedFailure lastFeedFailure = new LastFeedFailure(latestJob.getJobInstance().getFeed().getName(),latestJob.getJobExecutionId(),latestJob.getEndTime(),!BatchJobExecution.JobStatus.FAILED.equals(latestJob.getStatus()));
            return lastFeedFailure;
         }
         else {
             return EMPTY_JOB;
         }
        },MetadataAccess.SERVICE);

    }

    public boolean hasFailure(LastFeedFailure lastFeedFailure) {
        if(lastFeedFailure != null && lastFeedFailure.isFailure()){
            String feedName = lastFeedFailure.getFeedName();
            LastFeedFailure lastAssessedFailure = lastAssessedFeedFailureMap.get(feedName);
               if (lastAssessedFailure == null || (lastAssessedFailure != null && lastAssessedFailure.isFailure() && lastFeedFailure.isAfter(lastAssessedFailure.getDateTime()))) {
                    //reassign it as the lastAssessedFailure
                    lastAssessedFeedFailureMap.put(feedName, lastFeedFailure);
                    return true;
                }
        }
        return false;

    }

    /**
     * Should we assess the failure.  If so mark the latest as being assesed as a failure
     */
    public boolean hasFailure(String feedName) {
        LastFeedFailure lastFeedFailure = lastFeedFailureMap.get(feedName);
        return hasFailure(lastFeedFailure);
    }


    public static class LastFeedFailure {

        private Long jobExecutionId;
        private String feedName;
        private DateTime dateTime;
        private boolean success = false;

        public LastFeedFailure() {

        }

        public LastFeedFailure(String feedName, Long jobExecutionId){
            this(feedName,jobExecutionId,DateTime.now());
        }
        public LastFeedFailure(String feedName, Long jobExecutionId,DateTime dateTime) {
           this(feedName,jobExecutionId,dateTime,false);
        }

        public LastFeedFailure(String feedName, Long jobExecutionId,DateTime dateTime,boolean success) {
            this.feedName = feedName;
            this.dateTime = dateTime;
            this.success = success;
        }

        public String getFeedName() {
            return feedName;
        }

        public void setFeedName(String feedName) {
            this.feedName = feedName;
        }

        public DateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(DateTime dateTime) {
            this.dateTime = dateTime;
        }

        public boolean isAfter(DateTime time) {
            return dateTime != null && dateTime.isAfter(time);
        }

        public boolean isFailure(){
            return !this.success;
        }
    }


}
