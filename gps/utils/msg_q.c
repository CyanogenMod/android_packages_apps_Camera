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
 */

#include "msg_q.h"

#define LOG_TAG "LocSvc_utils_q"
#include "log_util.h"

#include "linked_list.h"
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

typedef struct msg_q {
   void* msg_list;                  /* Linked list to store information */
   pthread_cond_t  list_cond;       /* Condition variable for waiting on msg queue */
   pthread_mutex_t list_mutex;      /* Mutex for exclusive access to message queue */
   int unblocked;                   /* Has this message queue been unblocked? */
} msg_q;

/*===========================================================================
FUNCTION    convert_linked_list_err_type

DESCRIPTION
   Converts from one set of enum values to another.

   linked_list_val: Value to convert to msg_q_enum_type

DEPENDENCIES
   N/A

RETURN VALUE
   Corresponding linked_list_enum_type in msg_q_enum_type

SIDE EFFECTS
   N/A

===========================================================================*/
static msq_q_err_type convert_linked_list_err_type(linked_list_err_type linked_list_val)
{
   switch( linked_list_val )
   {
   case eLINKED_LIST_SUCCESS:
      return eMSG_Q_SUCCESS;
   case eLINKED_LIST_INVALID_PARAMETER:
      return eMSG_Q_INVALID_PARAMETER;
   case eLINKED_LIST_INVALID_HANDLE:
      return eMSG_Q_INVALID_HANDLE;
   case eLINKED_LIST_UNAVAILABLE_RESOURCE:
      return eMSG_Q_UNAVAILABLE_RESOURCE;
   case eLINKED_LIST_INSUFFICIENT_BUFFER:
      return eMSG_Q_INSUFFICIENT_BUFFER;

   case eLINKED_LIST_FAILURE_GENERAL:
   default:
      return eMSG_Q_FAILURE_GENERAL;
   }
}

/* ----------------------- END INTERNAL FUNCTIONS ---------------------------------------- */

/*===========================================================================

  FUNCTION:   msg_q_init

  ===========================================================================*/
msq_q_err_type msg_q_init(void** msg_q_data)
{
   if( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_PARAMETER;
   }

   msg_q* tmp_msg_q;
   tmp_msg_q = (msg_q*)calloc(1, sizeof(msg_q));
   if( tmp_msg_q == NULL )
   {
      LOC_LOGE("%s: Unable to allocate space for message queue!\n", __FUNCTION__);
      return eMSG_Q_FAILURE_GENERAL;
   }

   if( linked_list_init(&tmp_msg_q->msg_list) != 0 )
   {
      LOC_LOGE("%s: Unable to initialize storage list!\n", __FUNCTION__);
      free(tmp_msg_q);
      return eMSG_Q_FAILURE_GENERAL;
   }

   if( pthread_mutex_init(&tmp_msg_q->list_mutex, NULL) != 0 )
   {
      LOC_LOGE("%s: Unable to initialize list mutex!\n", __FUNCTION__);
      linked_list_destroy(&tmp_msg_q->msg_list);
      free(tmp_msg_q);
      return eMSG_Q_FAILURE_GENERAL;
   }

   if( pthread_cond_init(&tmp_msg_q->list_cond, NULL) != 0 )
   {
      LOC_LOGE("%s: Unable to initialize msg q cond var!\n", __FUNCTION__);
      linked_list_destroy(&tmp_msg_q->msg_list);
      pthread_mutex_destroy(&tmp_msg_q->list_mutex);
      free(tmp_msg_q);
      return eMSG_Q_FAILURE_GENERAL;
   }

   tmp_msg_q->unblocked = 0;

   *msg_q_data = tmp_msg_q;

   return eMSG_Q_SUCCESS;
}

/*===========================================================================

  FUNCTION:   msg_q_destroy

  ===========================================================================*/
msq_q_err_type msg_q_destroy(void** msg_q_data)
{
   if( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_HANDLE;
   }

   msg_q* p_msg_q = (msg_q*)*msg_q_data;

   linked_list_destroy(&p_msg_q->msg_list);
   pthread_mutex_destroy(&p_msg_q->list_mutex);
   pthread_cond_destroy(&p_msg_q->list_cond);

   p_msg_q->unblocked = 0;

   free(*msg_q_data);
   *msg_q_data = NULL;

   return eMSG_Q_SUCCESS;
}

/*===========================================================================

  FUNCTION:   msg_q_snd

  ===========================================================================*/
msq_q_err_type msg_q_snd(void* msg_q_data, void* msg_obj, void (*dealloc)(void*))
{
   msq_q_err_type rv;
   if( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_HANDLE;
   }
   if( msg_obj == NULL )
   {
      LOC_LOGE("%s: Invalid msg_obj parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_PARAMETER;
   }

   msg_q* p_msg_q = (msg_q*)msg_q_data;

   pthread_mutex_lock(&p_msg_q->list_mutex);
   LOC_LOGD("%s: Sending message with handle = 0x%08X\n", __FUNCTION__, msg_obj);

   if( p_msg_q->unblocked )
   {
      LOC_LOGE("%s: Message queue has been unblocked.\n", __FUNCTION__);
      pthread_mutex_unlock(&p_msg_q->list_mutex);
      return eMSG_Q_UNAVAILABLE_RESOURCE;
   }

   rv = convert_linked_list_err_type(linked_list_add(p_msg_q->msg_list, msg_obj, dealloc));

   /* Show data is in the message queue. */
   pthread_cond_signal(&p_msg_q->list_cond);

   pthread_mutex_unlock(&p_msg_q->list_mutex);

   LOC_LOGD("%s: Finished Sending message with handle = 0x%08X\n", __FUNCTION__, msg_obj);

   return rv;
}

/*===========================================================================

  FUNCTION:   msg_q_rcv

  ===========================================================================*/
msq_q_err_type msg_q_rcv(void* msg_q_data, void** msg_obj)
{
   msq_q_err_type rv;
   if( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_HANDLE;
   }

   if( msg_obj == NULL )
   {
      LOC_LOGE("%s: Invalid msg_obj parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_PARAMETER;
   }

   msg_q* p_msg_q = (msg_q*)msg_q_data;

   LOC_LOGD("%s: Waiting on message\n", __FUNCTION__);

   pthread_mutex_lock(&p_msg_q->list_mutex);

   if( p_msg_q->unblocked )
   {
      LOC_LOGE("%s: Message queue has been unblocked.\n", __FUNCTION__);
      pthread_mutex_unlock(&p_msg_q->list_mutex);
      return eMSG_Q_UNAVAILABLE_RESOURCE;
   }

   /* Wait for data in the message queue */
   while( linked_list_empty(p_msg_q->msg_list) && !p_msg_q->unblocked )
   {
      pthread_cond_wait(&p_msg_q->list_cond, &p_msg_q->list_mutex);
   }

   rv = convert_linked_list_err_type(linked_list_remove(p_msg_q->msg_list, msg_obj));

   pthread_mutex_unlock(&p_msg_q->list_mutex);

   LOC_LOGD("%s: Received message 0x%08X rv = %d\n", __FUNCTION__, *msg_obj, rv);

   return rv;
}

/*===========================================================================

  FUNCTION:   msg_q_flush

  ===========================================================================*/
msq_q_err_type msg_q_flush(void* msg_q_data)
{
   msq_q_err_type rv;
   if ( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_HANDLE;
   }

   msg_q* p_msg_q = (msg_q*)msg_q_data;

   LOC_LOGD("%s: Flushing Message Queue\n", __FUNCTION__);

   pthread_mutex_lock(&p_msg_q->list_mutex);

   /* Remove all elements from the list */
   rv = convert_linked_list_err_type(linked_list_flush(p_msg_q->msg_list));

   pthread_mutex_unlock(&p_msg_q->list_mutex);

   LOC_LOGD("%s: Message Queue flushed\n", __FUNCTION__);

   return rv;
}

/*===========================================================================

  FUNCTION:   msg_q_unblock

  ===========================================================================*/
msq_q_err_type msg_q_unblock(void* msg_q_data)
{
   if ( msg_q_data == NULL )
   {
      LOC_LOGE("%s: Invalid msg_q_data parameter!\n", __FUNCTION__);
      return eMSG_Q_INVALID_HANDLE;
   }

   msg_q* p_msg_q = (msg_q*)msg_q_data;
   pthread_mutex_lock(&p_msg_q->list_mutex);

   if( p_msg_q->unblocked )
   {
      LOC_LOGE("%s: Message queue has been unblocked.\n", __FUNCTION__);
      pthread_mutex_unlock(&p_msg_q->list_mutex);
      return eMSG_Q_UNAVAILABLE_RESOURCE;
   }

   LOC_LOGD("%s: Unblocking Message Queue\n", __FUNCTION__);
   /* Unblocking message queue */
   p_msg_q->unblocked = 1;

   /* Allow all the waiters to wake up */
   pthread_cond_broadcast(&p_msg_q->list_cond);

   pthread_mutex_unlock(&p_msg_q->list_mutex);

   LOC_LOGD("%s: Message Queue unblocked\n", __FUNCTION__);

   return eMSG_Q_SUCCESS;
}
