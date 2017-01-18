/**
 * 
 */
package com.thinkbiganalytics.metadata.jpa.alerts;

import java.io.Serializable;
import java.security.Principal;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import com.thinkbiganalytics.alerts.api.Alert;
import com.thinkbiganalytics.alerts.api.Alert.State;
import com.thinkbiganalytics.alerts.api.AlertChangeEvent;
import com.thinkbiganalytics.metadata.jpa.alerts.JpaAlert.AlertContentConverter;
import com.thinkbiganalytics.security.UsernamePrincipal;

/**
 *
 * @author Sean Felten
 */
@Embeddable
@Table(name = "KYLO_ALERT_CHANGE")
public class JpaAlertChangeEvent implements AlertChangeEvent, Comparable<AlertChangeEvent> {
    
    @Type(type = "com.thinkbiganalytics.jpa.PersistentDateTimeAsMillisLong")
    @Column(name = "CHANGE_TIME")
    private DateTime changeTime;

    @Column(name = "USER", columnDefinition = "varchar(128)")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATE", nullable = false)
    private Alert.State state;
    
    @Column(name = "DESCRIPTION", length = 255)
    private String description;
    
    @Column(name = "CONTENT")
    @Convert(converter = AlertContentConverter.class)
    private Serializable content;
    
    public JpaAlertChangeEvent() {
        super();
    }
    
    
    public JpaAlertChangeEvent(State state, Principal user) {
        this(state, user, null, null);
    }
    
    public JpaAlertChangeEvent(State state, Principal user, String descr) {
        this(state, user, descr, null);
    }
    
    public JpaAlertChangeEvent(State state, Principal user, String descr, Serializable content) {
        super();
        this.changeTime = DateTime.now();
        this.state = state;
        this.content = content;
        this.username = user != null ? user.getName() : null;
        setDescription(descr);
    }


    /* (non-Javadoc)
     * @see com.thinkbiganalytics.alerts.api.AlertChangeEvent#getChangeTime()
     */
    @Override
    public DateTime getChangeTime() {
        return this.changeTime;
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.alerts.api.AlertChangeEvent#getUser()
     */
    @Override
    public Principal getUser() {
        return new UsernamePrincipal(this.username);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.alerts.api.AlertChangeEvent#getState()
     */
    @Override
    public Alert.State getState() {
        return this.state;
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.alerts.api.AlertChangeEvent#getDescription()
     */
    @Override
    public String getDescription() {
        return this.description;
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.alerts.api.AlertChangeEvent#getContent()
     */
    @Override
    @SuppressWarnings("unchecked")
    public <C extends Serializable> C getContent() {
        return (C) this.content;
    }

    public void setChangeTime(DateTime changeTime) {
        this.changeTime = changeTime;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String user) {
        this.username = user;
    }
    
    public void setDescription(String descr) {
        this.description = descr == null || descr.length() <= 255 ? descr : descr.substring(0, 252) + "...";
    }

    public void setState(Alert.State state) {
        this.state = state;
    }

    public void setContent(Serializable content) {
        this.content = content;
    }


    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(AlertChangeEvent that) {
        int result = that.getChangeTime().compareTo(this.changeTime);
        return result == 0 ? this.state.compareTo(that.getState()) : result;
    }

}
