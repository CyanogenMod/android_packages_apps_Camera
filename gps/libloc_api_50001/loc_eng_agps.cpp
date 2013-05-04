/* Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation, nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#define LOG_NDDEBUG 0
#define LOG_TAG "LocSvc_eng"

#include <loc_eng_agps.h>
#include <loc_eng_log.h>
#include <log_util.h>
#include <loc_eng_dmn_conn_handler.h>
#include <loc_eng_dmn_conn.h>

//======================================================================
// C callbacks
//======================================================================

// This is given to linked_list_add as the dealloc callback
// data -- an instance of Subscriber
static void deleteObj(void* data)
{
    delete (Subscriber*)data;
}

// This is given to linked_list_search() as the comparison callback
// when the state manchine needs to process for particular subscriber
// fromCaller -- caller provides this obj
// fromList -- linked_list_search() function take this one from list
static bool hasSubscriber(void* fromCaller, void* fromList)
{
    Notification* notification = (Notification*)fromCaller;
    Subscriber* s1 = (Subscriber*)fromList;

    return s1->forMe(*notification);
}

// This is gvien to linked_list_search() to notify subscriber objs
// when the state machine needs to inform all subscribers of resource
// status changes, e.g. when resource is GRANTED.
// fromCaller -- caller provides this ptr to a Notification obj.
// fromList -- linked_list_search() function take this one from list
static bool notifySubscriber(void* fromCaller, void* fromList)
{
    Notification* notification = (Notification*)fromCaller;
    Subscriber* s1 = (Subscriber*)fromList;

    // we notify every subscriber indiscriminatively
    // each subscriber decides if this notification is interesting.
    return s1->notifyRsrcStatus(*notification) &&
           // if we do not want to delete the subscriber from the
           // the list, we must set this to false so this function
           // returns false
           notification->postNotifyDelete;
}

//======================================================================
// Notification
//======================================================================
const int Notification::BROADCAST_ALL = 0x80000000;
const int Notification::BROADCAST_ACTIVE = 0x80000001;
const int Notification::BROADCAST_INACTIVE = 0x80000002;


//======================================================================
// Subscriber:  BITSubscriber / ATLSubscriber / WIFISubscriber
//======================================================================
bool Subscriber::forMe(Notification &notification)
{
    if (NULL != notification.rcver) {
        return equals(notification.rcver);
    } else {
        return Notification::BROADCAST_ALL == notification.groupID ||
            (Notification::BROADCAST_ACTIVE == notification.groupID &&
             !isInactive()) ||
            (Notification::BROADCAST_INACTIVE == notification.groupID &&
             isInactive());
    }
}
bool BITSubscriber::equals(const Subscriber *s) const
{
    BITSubscriber* bitS = (BITSubscriber*)s;

    return (ID == bitS->ID &&
            (INADDR_NONE != (unsigned int)ID ||
             0 == strncmp(ipv6Addr, bitS->ipv6Addr, sizeof(ipv6Addr))));
}

bool BITSubscriber::notifyRsrcStatus(Notification &notification)
{
    bool notify = forMe(notification);

    if (notify) {
        switch(notification.rsrcStatus)
        {
        case RSRC_UNSUBSCRIBE:
        case RSRC_RELEASED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON,
                GPSONE_LOC_API_IF_RELEASE_SUCCESS);
            break;
        case RSRC_DENIED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON,
                GPSONE_LOC_API_IF_FAILURE);
            break;
        case RSRC_GRANTED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                LOC_ENG_IF_REQUEST_SENDER_ID_GPSONE_DAEMON,
                GPSONE_LOC_API_IF_REQUEST_SUCCESS);
            break;
        default:
            notify = false;
        }
    }

    return notify;
}

bool ATLSubscriber::notifyRsrcStatus(Notification &notification)
{
    bool notify = forMe(notification);

    if (notify) {
        switch(notification.rsrcStatus)
        {
        case RSRC_UNSUBSCRIBE:
        case RSRC_RELEASED:
            ((LocApiAdapter*)mLocAdapter)->atlCloseStatus(ID, 1);
            break;
        case RSRC_DENIED:
        {
#ifdef FEATURE_IPV6
            AGpsType type = mBackwardCompatibleMode ?
                              AGPS_TYPE_INVALID : mStateMachine->getType();
            ((LocApiAdapter*)mLocAdapter)->atlOpenStatus(ID, 0,
                                            (char*)mStateMachine->getAPN(),
                                            mStateMachine->getBearer(),
                                            type);
#else
            AGpsType type = mStateMachine->getType();
            ((LocApiAdapter*)mLocAdapter)->atlOpenStatus(ID, 0,
                                            (char*)mStateMachine->getAPN(),
                                            type);
#endif
        }
            break;
        case RSRC_GRANTED:
        {
#ifdef FEATURE_IPV6
            AGpsType type = mBackwardCompatibleMode ?
                              AGPS_TYPE_INVALID : mStateMachine->getType();
            ((LocApiAdapter*)mLocAdapter)->atlOpenStatus(ID, 1,
                                            (char*)mStateMachine->getAPN(),
                                            mStateMachine->getBearer(),
                                            type);
#else
            AGpsType type = mStateMachine->getType();
            ((LocApiAdapter*)mLocAdapter)->atlOpenStatus(ID, 1,
                                            (char*)mStateMachine->getAPN(),
                                            type);
#endif
        }
            break;
        default:
            notify = false;
        }
    }

    return notify;
}

#ifdef FEATURE_IPV6
bool WIFISubscriber::notifyRsrcStatus(Notification &notification)
{
    bool notify = forMe(notification);

    if (notify) {
        switch(notification.rsrcStatus)
        {
        case RSRC_UNSUBSCRIBE:
            break;
        case RSRC_RELEASED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                senderId,
                GPSONE_LOC_API_IF_RELEASE_SUCCESS);
            break;
        case RSRC_DENIED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                senderId,
                GPSONE_LOC_API_IF_FAILURE);
            break;
        case RSRC_GRANTED:
            loc_eng_dmn_conn_loc_api_server_data_conn(
                senderId,
                GPSONE_LOC_API_IF_REQUEST_SUCCESS);
            break;
        default:
            notify = false;
        }
    }

    return notify;
}
#endif

//======================================================================
// AgpsState:  AgpsReleasedState / AgpsPendingState / AgpsAcquiredState
//======================================================================

// AgpsReleasedState
class AgpsReleasedState : public AgpsState
{
    friend class AgpsStateMachine;

    inline AgpsReleasedState(AgpsStateMachine* stateMachine) :
        AgpsState(stateMachine)
    { mReleasedState = this; }

    inline ~AgpsReleasedState() {}
public:
    virtual AgpsState* onRsrcEvent(AgpsRsrcStatus event, void* data);
    inline virtual char* whoami() {return (char*)"AgpsReleasedState";}
};

AgpsState* AgpsReleasedState::onRsrcEvent(AgpsRsrcStatus event, void* data)
{
    if (mStateMachine->hasSubscribers()) {
        LOC_LOGE("Error: %s subscriber list not empty!!!", whoami());
        // I don't know how to recover from it.  I am adding this rather
        // for debugging purpose.
    }

    AgpsState* nextState = this;;
    switch (event)
    {
    case RSRC_SUBSCRIBE:
    {
        // no notification until we get RSRC_GRANTED
        // but we need to add subscriber to the list
        mStateMachine->addSubscriber((Subscriber*)data);
        // move the state to PENDING
        nextState = mPendingState;

        // request from connecivity service for NIF
        mStateMachine->sendRsrcRequest(GPS_REQUEST_AGPS_DATA_CONN);
    }
        break;

    case RSRC_UNSUBSCRIBE:
    {
        // the list should really be empty, nothing to remove.
        // but we might as well just tell the client it is
        // unsubscribed.  False tolerance, right?
        Subscriber* subscriber = (Subscriber*) data;
        Notification notification(subscriber, event, false);
        subscriber->notifyRsrcStatus(notification);
    }
        // break;
    case RSRC_GRANTED:
    case RSRC_RELEASED:
    case RSRC_DENIED:
    default:
        LOC_LOGW("%s: unrecognized event %d", whoami(), event);
        // no state change.
        break;
    }

    LOC_LOGD("onRsrcEvent, old state %s, new state %s, event %d",
             whoami(), nextState->whoami(), event);
    return nextState;
}

// AgpsPendingState
class AgpsPendingState : public AgpsState
{
    friend class AgpsStateMachine;

    inline AgpsPendingState(AgpsStateMachine* stateMachine) :
        AgpsState(stateMachine)
    { mPendingState = this; }

    inline ~AgpsPendingState() {}
public:
    virtual AgpsState* onRsrcEvent(AgpsRsrcStatus event, void* data);
    inline virtual char* whoami() {return (char*)"AgpsPendingState";}
};

AgpsState* AgpsPendingState::onRsrcEvent(AgpsRsrcStatus event, void* data)
{
    AgpsState* nextState = this;;
    switch (event)
    {
    case RSRC_SUBSCRIBE:
    {
        // already requested for NIF resource,
        // do nothing until we get RSRC_GRANTED indication
        // but we need to add subscriber to the list
        mStateMachine->addSubscriber((Subscriber*)data);
        // no state change.
    }
        break;

    case RSRC_UNSUBSCRIBE:
    {
        Subscriber* subscriber = (Subscriber*) data;
        if (subscriber->waitForCloseComplete()) {
            subscriber->setInactive();
        } else {
            // auto notify this subscriber of the unsubscribe
            Notification notification(subscriber, event, true);
            mStateMachine->notifySubscribers(notification);
        }

        // now check if there is any subscribers left
        if (!mStateMachine->hasSubscribers()) {
            // no more subscribers, move to RELEASED state
            nextState = mReleasedState;

            // tell connecivity service we can release NIF
            mStateMachine->sendRsrcRequest(GPS_RELEASE_AGPS_DATA_CONN);
        } else if (!mStateMachine->hasActiveSubscribers()) {
            // only inactive subscribers, move to RELEASING state
            nextState = mReleasingState;

            // tell connecivity service we can release NIF
            mStateMachine->sendRsrcRequest(GPS_RELEASE_AGPS_DATA_CONN);
        }
    }
        break;

    case RSRC_GRANTED:
    {
        nextState = mAcquiredState;
        Notification notification(Notification::BROADCAST_ACTIVE, event, false);
        // notify all subscribers NIF resource GRANTED
        // by setting false, we keep subscribers on the linked list
        mStateMachine->notifySubscribers(notification);
    }
        break;

    case RSRC_RELEASED:
        // no state change.
        // we are expecting either GRANTED or DENIED.  Handling RELEASED
        // may like break our state machine in race conditions.
        break;

    case RSRC_DENIED:
    {
        nextState = mReleasedState;
        Notification notification(Notification::BROADCAST_ALL, event, true);
        // notify all subscribers NIF resource RELEASED or DENIED
        // by setting true, we remove subscribers from the linked list
        mStateMachine->notifySubscribers(notification);
    }
        break;

    default:
        LOC_LOGE("%s: unrecognized event %d", whoami(), event);
        // no state change.
    }

    LOC_LOGD("onRsrcEvent, old state %s, new state %s, event %d",
             whoami(), nextState->whoami(), event);
    return nextState;
}


class AgpsAcquiredState : public AgpsState
{
    friend class AgpsStateMachine;

    inline AgpsAcquiredState(AgpsStateMachine* stateMachine) :
        AgpsState(stateMachine)
    { mAcquiredState = this; }

    inline ~AgpsAcquiredState() {}
public:
    virtual AgpsState* onRsrcEvent(AgpsRsrcStatus event, void* data);
    inline virtual char* whoami() { return (char*)"AgpsAcquiredState"; }
};


AgpsState* AgpsAcquiredState::onRsrcEvent(AgpsRsrcStatus event, void* data)
{
    AgpsState* nextState = this;
    switch (event)
    {
    case RSRC_SUBSCRIBE:
    {
        // we already have the NIF resource, simply notify subscriber
        Subscriber* subscriber = (Subscriber*) data;
        // we have rsrc in hand, so grant it right away
        Notification notification(subscriber, RSRC_GRANTED, false);
        subscriber->notifyRsrcStatus(notification);
        // add subscriber to the list
        mStateMachine->addSubscriber(subscriber);
        // no state change.
    }
        break;

    case RSRC_UNSUBSCRIBE:
    {
        Subscriber* subscriber = (Subscriber*) data;
        if (subscriber->waitForCloseComplete()) {
            subscriber->setInactive();
        } else {
            // auto notify this subscriber of the unsubscribe
            Notification notification(subscriber, event, true);
            mStateMachine->notifySubscribers(notification);
        }

        // now check if there is any subscribers left
        if (!mStateMachine->hasSubscribers()) {
            // no more subscribers, move to RELEASED state
            nextState = mReleasedState;

            // tell connecivity service we can release NIF
            mStateMachine->sendRsrcRequest(GPS_RELEASE_AGPS_DATA_CONN);
        } else if (!mStateMachine->hasActiveSubscribers()) {
            // only inactive subscribers, move to RELEASING state
            nextState = mReleasingState;

            // tell connecivity service we can release NIF
            mStateMachine->sendRsrcRequest(GPS_RELEASE_AGPS_DATA_CONN);
        }
    }
        break;

    case RSRC_GRANTED:
        LOC_LOGW("%s: %d, RSRC_GRANTED already received", whoami(), event);
        // no state change.
        break;

    case RSRC_RELEASED:
    {
        LOC_LOGW("%s: %d, a force rsrc release", whoami(), event);
        nextState = mReleasedState;
        Notification notification(Notification::BROADCAST_ALL, event, true);
        // by setting true, we remove subscribers from the linked list
        mStateMachine->notifySubscribers(notification);
    }
        break;

    case RSRC_DENIED:
        // no state change.
        // we are expecting RELEASED.  Handling DENIED
        // may like break our state machine in race conditions.
        break;

    default:
        LOC_LOGE("%s: unrecognized event %d", whoami(), event);
        // no state change.
    }

    LOC_LOGD("onRsrcEvent, old state %s, new state %s, event %d",
             whoami(), nextState->whoami(), event);
    return nextState;
}

// AgpsPendingState
class AgpsReleasingState : public AgpsState
{
    friend class AgpsStateMachine;

    inline AgpsReleasingState(AgpsStateMachine* stateMachine) :
        AgpsState(stateMachine)
    { mReleasingState = this; }

    inline ~AgpsReleasingState() {}
public:
    virtual AgpsState* onRsrcEvent(AgpsRsrcStatus event, void* data);
    inline virtual char* whoami() {return (char*)"AgpsReleasingState";}
};

AgpsState* AgpsReleasingState::onRsrcEvent(AgpsRsrcStatus event, void* data)
{
    AgpsState* nextState = this;;
    switch (event)
    {
    case RSRC_SUBSCRIBE:
    {
        // already requested for NIF resource,
        // do nothing until we get RSRC_GRANTED indication
        // but we need to add subscriber to the list
        mStateMachine->addSubscriber((Subscriber*)data);
        // no state change.
    }
        break;

    case RSRC_UNSUBSCRIBE:
    {
        Subscriber* subscriber = (Subscriber*) data;
        if (subscriber->waitForCloseComplete()) {
            subscriber->setInactive();
        } else {
            // auto notify this subscriber of the unsubscribe
            Notification notification(subscriber, event, true);
            mStateMachine->notifySubscribers(notification);
        }

        // now check if there is any subscribers left
        if (!mStateMachine->hasSubscribers()) {
            // no more subscribers, move to RELEASED state
            nextState = mReleasedState;
        }
    }
        break;

    case RSRC_DENIED:
        // A race condition subscriber unsubscribes before AFW denies resource.
    case RSRC_RELEASED:
    {
        nextState = mAcquiredState;
        Notification notification(Notification::BROADCAST_INACTIVE, event, true);
        // notify all subscribers that are active NIF resource RELEASE
        // by setting false, we keep subscribers on the linked list
        mStateMachine->notifySubscribers(notification);

        if (mStateMachine->hasSubscribers()) {
            nextState = mPendingState;
            // request from connecivity service for NIF
            mStateMachine->sendRsrcRequest(GPS_REQUEST_AGPS_DATA_CONN);
        } else {
            nextState = mReleasedState;
        }
    }
        break;

    case RSRC_GRANTED:
    default:
        LOC_LOGE("%s: unrecognized event %d", whoami(), event);
        // no state change.
    }

    LOC_LOGD("onRsrcEvent, old state %s, new state %s, event %d",
             whoami(), nextState->whoami(), event);
    return nextState;
}


//======================================================================
// AgpsStateMachine
//======================================================================

AgpsStateMachine::AgpsStateMachine(void (*servicer)(AGpsStatus* status),
                                   AGpsType type,
                                   bool enforceSingleSubscriber) :
    mServicer(servicer), mType(type),
    mStatePtr(new AgpsReleasedState(this)),
    mAPN(NULL),
    mAPNLen(0),
    mEnforceSingleSubscriber(enforceSingleSubscriber)
{
    linked_list_init(&mSubscribers);

    // setting up mReleasedState
    mStatePtr->mPendingState = new AgpsPendingState(this);
    mStatePtr->mAcquiredState = new AgpsAcquiredState(this);
    mStatePtr->mReleasingState = new AgpsReleasingState(this);

    // setting up mAcquiredState
    mStatePtr->mAcquiredState->mReleasedState = mStatePtr;
    mStatePtr->mAcquiredState->mPendingState = mStatePtr->mPendingState;
    mStatePtr->mAcquiredState->mReleasingState = mStatePtr->mReleasingState;

    // setting up mPendingState
    mStatePtr->mPendingState->mAcquiredState = mStatePtr->mAcquiredState;
    mStatePtr->mPendingState->mReleasedState = mStatePtr;
    mStatePtr->mPendingState->mReleasingState = mStatePtr->mReleasingState;

    // setting up mReleasingState
    mStatePtr->mReleasingState->mReleasedState = mStatePtr;
    mStatePtr->mReleasingState->mPendingState = mStatePtr->mPendingState;
    mStatePtr->mReleasingState->mAcquiredState = mStatePtr->mAcquiredState;
}

AgpsStateMachine::~AgpsStateMachine()
{
    dropAllSubscribers();

    // free the 3 states.  We must read out all 3 pointers first.
    // Otherwise we run the risk of getting pointers from already
    // freed memory.
    AgpsState* acquiredState = mStatePtr->mAcquiredState;
    AgpsState* releasedState = mStatePtr->mReleasedState;
    AgpsState* pendindState = mStatePtr->mPendingState;
    AgpsState* releasingState = mStatePtr->mReleasingState;

    delete acquiredState;
    delete releasedState;
    delete pendindState;
    delete releasingState;
    linked_list_destroy(&mSubscribers);

    if (NULL != mAPN) {
        delete[] mAPN;
        mAPN = NULL;
    }
}

void AgpsStateMachine::setAPN(const char* apn, unsigned int len)
{
    if (NULL != mAPN) {
        delete mAPN;
    }

    if (NULL != apn) {
        mAPN = new char[len+1];
        memcpy(mAPN, apn, len);
        mAPN[len] = NULL;

        mAPNLen = len;
    } else {
        mAPN = NULL;
        mAPNLen = 0;
    }
}

void AgpsStateMachine::onRsrcEvent(AgpsRsrcStatus event)
{
    switch (event)
    {
    case RSRC_GRANTED:
    case RSRC_RELEASED:
    case RSRC_DENIED:
        mStatePtr = mStatePtr->onRsrcEvent(event, NULL);
        break;
    default:
        LOC_LOGW("AgpsStateMachine: unrecognized event %d", event);
        break;
    }
}

void AgpsStateMachine::notifySubscribers(Notification& notification) const
{
    if (notification.postNotifyDelete) {
        // just any non NULL value to get started
        Subscriber* s = (Subscriber*)~0;
        while (NULL != s) {
            s = NULL;
            // if the last param sets to true, _search will delete
            // the node from the list for us.  But the problem is
            // once that is done, _search returns, leaving the
            // rest of the list unprocessed.  So we need a loop.
            linked_list_search(mSubscribers, (void**)&s, notifySubscriber,
                               (void*)&notification, true);
        }
    } else {
        // no loop needed if it the last param sets to false, which
        // mean nothing gets deleted from the list.
        linked_list_search(mSubscribers, NULL, notifySubscriber,
                           (void*)&notification, false);
    }
}

void AgpsStateMachine::addSubscriber(Subscriber* subscriber) const
{
    Subscriber* s = NULL;
    Notification notification((const Subscriber*)subscriber);
    linked_list_search(mSubscribers, (void**)&s,
                       hasSubscriber, (void*)&notification, false);

    if (NULL == s) {
        linked_list_add(mSubscribers, subscriber->clone(), deleteObj);
    }
}

void AgpsStateMachine::sendRsrcRequest(AGpsStatusValue action) const
{
    Subscriber* s = NULL;
    Notification notification(Notification::BROADCAST_ACTIVE);
    linked_list_search(mSubscribers, (void**)&s, hasSubscriber,
                       (void*)&notification, false);

    if ((NULL == s) == (GPS_RELEASE_AGPS_DATA_CONN == action)) {
        AGpsStatus nifRequest;
        nifRequest.size = sizeof(nifRequest);
        nifRequest.type = mType;
        nifRequest.status = action;

#ifdef FEATURE_IPV6
        if (s == NULL) {
            nifRequest.ipv4_addr = INADDR_NONE;
            nifRequest.ipv6_addr[0] = 0;
            nifRequest.ssid[0] = '\0';
            nifRequest.password[0] = '\0';
        } else {
            s->setIPAddresses(nifRequest.ipv4_addr, (char*)nifRequest.ipv6_addr);
            s->setWifiInfo(nifRequest.ssid, nifRequest.password);
        }
#else
        if (s == NULL) {
            nifRequest.ipaddr = INADDR_NONE;
        } else {
            nifRequest.ipaddr = s->ID;
        }
#endif

        CALLBACK_LOG_CALLFLOW("agps_cb", %s, loc_get_agps_status_name(action));
        (*mServicer)(&nifRequest);
    }
}

void AgpsStateMachine::subscribeRsrc(Subscriber *subscriber)
{
  if (mEnforceSingleSubscriber && hasSubscribers()) {
      Notification notification(Notification::BROADCAST_ALL, RSRC_DENIED, true);
      notifySubscriber(&notification, subscriber);
  } else {
      mStatePtr = mStatePtr->onRsrcEvent(RSRC_SUBSCRIBE, (void*)subscriber);
  }
}

bool AgpsStateMachine::unsubscribeRsrc(Subscriber *subscriber)
{
    Subscriber* s = NULL;
    Notification notification((const Subscriber*)subscriber);
    linked_list_search(mSubscribers, (void**)&s,
                       hasSubscriber, (void*)&notification, false);

    if (NULL != s) {
        mStatePtr = mStatePtr->onRsrcEvent(RSRC_UNSUBSCRIBE, (void*)s);
        return true;
    }
    return false;
}

bool AgpsStateMachine::hasActiveSubscribers() const
{
    Subscriber* s = NULL;
    Notification notification(Notification::BROADCAST_ACTIVE);
    linked_list_search(mSubscribers, (void**)&s,
                       hasSubscriber, (void*)&notification, false);
    return NULL != s;
}
