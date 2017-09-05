package com.thinkbiganalytics.metadata.jpa.jobrepo.nifi;

/*-
 * #%L
 * thinkbig-operational-metadata-jpa
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

import com.thinkbiganalytics.metadata.jpa.feed.security.FeedOpsAccessControlRepository;

import org.joda.time.DateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QueryDslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring data repository for {@link JpaNifiFeedProcessorStats}
 */
public interface NifiFeedProcessorStatisticsRepository extends JpaRepository<JpaNifiFeedProcessorStats, String>, QueryDslPredicateExecutor<JpaNifiFeedProcessorStats> {

    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + FeedOpsAccessControlRepository.JOIN_ACL_TO_FEED
                   + " where stats.minEventTime between :startTime and :endTime "
                   + "and " + FeedOpsAccessControlRepository.WHERE_PRINCIPALS_MATCH)
    List<JpaNifiFeedProcessorStats> findWithinTimeWindowWithAcl(@Param("startTime") DateTime start, @Param("endTime") DateTime end);

    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + "where stats.minEventTime between :startTime and :endTime ")
    List<JpaNifiFeedProcessorStats> findWithinTimeWindowWithoutAcl(@Param("startTime") DateTime start, @Param("endTime") DateTime end);

    @Query(value = "select max(stats.maxEventId) from JpaNifiFeedProcessorStats as stats")
    Long findMaxEventId();

    @Query(value = "select max(stats.maxEventId) from JpaNifiFeedProcessorStats as stats where stats.clusterNodeId = :clusterNodeId")
    Long findMaxEventId(@Param("clusterNodeId") String clusterNodeId);


    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + " and feed.name = :feedName "
                   + FeedOpsAccessControlRepository.JOIN_ACL_TO_FEED
                   + " where stats.minEventTime between :startTime and :endTime "
                   + "and " + FeedOpsAccessControlRepository.WHERE_PRINCIPALS_MATCH
                   + " and stats.errorMessages is not null ")
    List<JpaNifiFeedProcessorStats> findWithErrorsWithinTimeWithAcl(@Param("feedName") String feedName, @Param("startTime") DateTime start, @Param("endTime") DateTime end);


    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + " and feed.name = :feedName "
                   + "where stats.minEventTime between :startTime and :endTime "
                   + " and stats.errorMessages is not null ")
    List<JpaNifiFeedProcessorStats> findWithErrorsWithinTimeWithoutAcl(@Param("feedName") String feedName, @Param("startTime") DateTime start, @Param("endTime") DateTime end);

    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + " and feed.name = :feedName"
                   + FeedOpsAccessControlRepository.JOIN_ACL_TO_FEED
                   + " where stats.minEventTime > :afterTimestamp "
                   + "and " + FeedOpsAccessControlRepository.WHERE_PRINCIPALS_MATCH
                   + " and stats.errorMessages is not null ")
    List<JpaNifiFeedProcessorStats> findWithErrorsAfterTimeWithAcl(@Param("feedName") String feedName, @Param("afterTimestamp") DateTime afterTimestamp);

    @Query(value = "select distinct stats from JpaNifiFeedProcessorStats as stats "
                   + "join JpaOpsManagerFeed as feed on feed.name = stats.feedName "
                   + " and feed.name = :feedName"
                   + " where stats.minEventTime > :afterTimestamp "
                   + " and stats.errorMessages is not null ")
    List<JpaNifiFeedProcessorStats> findWithErrorsAfterTimeWithoutAcl(@Param("feedName") String feedName, @Param("afterTimestamp") DateTime afterTimestamp);


}
