/*
 * Copyright (C) 2006 The Android Open Source Project
 *
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
 */

/*
 * ISSUES:
 *
 */

/**
 * TODO
 *
 *
 */


#ifndef ANDROID_RIL_CDMA_SMS_H
#define ANDROID_RIL_CDMA_SMS_H 1

#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Used by RIL_REQUEST_CDMA_SEND_SMS and RIL_UNSOL_RESPONSE_CDMA_NEW_SMS */

#define RIL_CDMA_SMS_ADDRESS_MAX     36
#define RIL_CDMA_SMS_SUBADDRESS_MAX  36
#define RIL_CDMA_SMS_BEARER_DATA_MAX 255

typedef enum {
    RIL_CDMA_SMS_DIGIT_MODE_4_BIT = 0,     /* DTMF digits */
    RIL_CDMA_SMS_DIGIT_MODE_8_BIT = 1,
    RIL_CDMA_SMS_DIGIT_MODE_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_DigitMode;

typedef enum {
    RIL_CDMA_SMS_NUMBER_MODE_NOT_DATA_NETWORK = 0,
    RIL_CDMA_SMS_NUMBER_MODE_DATA_NETWORK     = 1,
    RIL_CDMA_SMS_NUMBER_MODE_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_NumberMode;

typedef enum {
    RIL_CDMA_SMS_NUMBER_TYPE_UNKNOWN                   = 0,
    RIL_CDMA_SMS_NUMBER_TYPE_INTERNATIONAL_OR_DATA_IP  = 1,
      /* INTERNATIONAL is used when number mode is not data network address.
       * DATA_IP is used when the number mode is data network address
       */
    RIL_CDMA_SMS_NUMBER_TYPE_NATIONAL_OR_INTERNET_MAIL = 2,
      /* NATIONAL is used when the number mode is not data network address.
       * INTERNET_MAIL is used when the number mode is data network address.
       * For INTERNET_MAIL, in the address data "digits", each byte contains
       * an ASCII character. Examples are "x@y.com,a@b.com - ref TIA/EIA-637A 3.4.3.3
       */
    RIL_CDMA_SMS_NUMBER_TYPE_NETWORK                   = 3,
    RIL_CDMA_SMS_NUMBER_TYPE_SUBSCRIBER                = 4,
    RIL_CDMA_SMS_NUMBER_TYPE_ALPHANUMERIC              = 5,
      /* GSM SMS: address value is GSM 7-bit chars */
    RIL_CDMA_SMS_NUMBER_TYPE_ABBREVIATED               = 6,
    RIL_CDMA_SMS_NUMBER_TYPE_RESERVED_7                = 7,
    RIL_CDMA_SMS_NUMBER_TYPE_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_NumberType;

typedef enum {
    RIL_CDMA_SMS_NUMBER_PLAN_UNKNOWN     = 0,
    RIL_CDMA_SMS_NUMBER_PLAN_TELEPHONY   = 1,      /* CCITT E.164 and E.163, including ISDN plan */
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_2  = 2,
    RIL_CDMA_SMS_NUMBER_PLAN_DATA        = 3,      /* CCITT X.121 */
    RIL_CDMA_SMS_NUMBER_PLAN_TELEX       = 4,      /* CCITT F.69 */
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_5  = 5,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_6  = 6,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_7  = 7,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_8  = 8,
    RIL_CDMA_SMS_NUMBER_PLAN_PRIVATE     = 9,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_10 = 10,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_11 = 11,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_12 = 12,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_13 = 13,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_14 = 14,
    RIL_CDMA_SMS_NUMBER_PLAN_RESERVED_15 = 15,
    RIL_CDMA_SMS_NUMBER_PLAN_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_NumberPlan;

typedef struct {
    RIL_CDMA_SMS_DigitMode digit_mode;
      /* Indicates 4-bit or 8-bit */
    RIL_CDMA_SMS_NumberMode number_mode;
      /* Used only when digitMode is 8-bit */
    RIL_CDMA_SMS_NumberType number_type;
      /* Used only when digitMode is 8-bit.
       * To specify an international address, use the following:
       * digitMode = RIL_CDMA_SMS_DIGIT_MODE_8_BIT
       * numberMode = RIL_CDMA_SMS_NOT_DATA_NETWORK
       * numberType = RIL_CDMA_SMS_NUMBER_TYPE_INTERNATIONAL_OR_DATA_IP
       * numberPlan = RIL_CDMA_SMS_NUMBER_PLAN_TELEPHONY
       * numberOfDigits = number of digits
       * digits = ASCII digits, e.g. '1', '2', '3'3, '4', and '5'
       */
    RIL_CDMA_SMS_NumberPlan number_plan;
      /* Used only when digitMode is 8-bit */
    unsigned char number_of_digits;
    unsigned char digits[ RIL_CDMA_SMS_ADDRESS_MAX ];
      /* Each byte in this array represnts a 40bit or 8-bit digit of address data */
} RIL_CDMA_SMS_Address;

typedef enum {
    RIL_CDMA_SMS_SUBADDRESS_TYPE_NSAP           = 0,    /* CCITT X.213 or ISO 8348 AD2 */
    RIL_CDMA_SMS_SUBADDRESS_TYPE_USER_SPECIFIED = 1,    /* e.g. X.25 */
    RIL_CDMA_SMS_SUBADDRESS_TYPE_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_SubaddressType;

typedef struct {
    RIL_CDMA_SMS_SubaddressType subaddressType;
    /* 1 means the last byte's lower 4 bits should be ignored */
    unsigned char odd;
    unsigned char number_of_digits;
    /* Each byte respresents a 8-bit digit of subaddress data */
    unsigned char digits[ RIL_CDMA_SMS_SUBADDRESS_MAX ];
} RIL_CDMA_SMS_Subaddress;

typedef struct {
    int uTeleserviceID;
    unsigned char bIsServicePresent;
    int uServicecategory;
    RIL_CDMA_SMS_Address sAddress;
    RIL_CDMA_SMS_Subaddress sSubAddress;
    int uBearerDataLen;
    unsigned char aBearerData[ RIL_CDMA_SMS_BEARER_DATA_MAX ];
} RIL_CDMA_SMS_Message;

/* Used by RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE */

typedef enum {
    RIL_CDMA_SMS_NO_ERROR       = 0,
    RIL_CDMA_SMS_ERROR          = 1,
    RIL_CDMA_SMS_ERROR_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_ErrorClass;

typedef struct {
    RIL_CDMA_SMS_ErrorClass uErrorClass;
    int uSMSCauseCode;  /* As defined in N.S00005, 6.5.2.125.
                           Currently, only 35 (resource shortage) and
                           39 (other terminal problem) are reported. */
} RIL_CDMA_SMS_Ack;

/* Used by RIL_REQUEST_CDMA_SMS_GET_BROADCAST_CONFIG and
   RIL_REQUEST_CDMA_SMS_SET_BROADCAST_CONFIG */

typedef struct {
    int service_category;
    int language;
    unsigned char selected;
} RIL_CDMA_BroadcastSmsConfigInfo;

/* Used by RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM */

typedef struct {
    int status;     /* Status of message.  See TS 27.005 3.1, "<stat>": */
                  /*      0 = "REC UNREAD"    */
                  /*      1 = "REC READ"      */
                  /*      2 = "STO UNSENT"    */
                  /*      3 = "STO SENT"      */

    RIL_CDMA_SMS_Message message;
} RIL_CDMA_SMS_WriteArgs;


/* Used by RIL_REQUEST_ENCODE_CDMA_SMS and RIL_REQUEST_DECODE_CDMA_SMS*/

#define RIL_CDMA_SMS_UDH_MAX_SND_SIZE           128
#define RIL_CDMA_SMS_UDH_EO_DATA_SEGMENT_MAX    131 /* 140 - 3 - 6 */
#define RIL_CDMA_SMS_MAX_UD_HEADERS         7
#define RIL_CDMA_SMS_USER_DATA_MAX     229
#define RIL_CDMA_SMS_ADDRESS_MAX            36
#define RIL_CDMA_SMS_UDH_LARGE_PIC_SIZE     128
#define RIL_CDMA_SMS_UDH_SMALL_PIC_SIZE     32
#define RIL_CDMA_SMS_UDH_VAR_PIC_SIZE       134
#define RIL_CDMA_SMS_UDH_ANIM_NUM_BITMAPS   4
#define RIL_CDMA_SMS_UDH_LARGE_BITMAP_SIZE  32
#define RIL_CDMA_SMS_UDH_SMALL_BITMAP_SIZE  8
#define RIL_CDMA_SMS_UDH_OTHER_SIZE         226
#define RIL_CDMA_SMS_IP_ADDRESS_SIZE        4

/* ------------------- */
/* ---- User Data ---- */
/* ------------------- */
typedef enum {
    RIL_CDMA_SMS_UDH_CONCAT_8         = 0x00,
    RIL_CDMA_SMS_UDH_SPECIAL_SM,
    /* 02 - 03    Reserved */
    RIL_CDMA_SMS_UDH_PORT_8           = 0x04,
    RIL_CDMA_SMS_UDH_PORT_16,
    RIL_CDMA_SMS_UDH_SMSC_CONTROL,
    RIL_CDMA_SMS_UDH_SOURCE,
    RIL_CDMA_SMS_UDH_CONCAT_16,
    RIL_CDMA_SMS_UDH_WCMP,
    RIL_CDMA_SMS_UDH_TEXT_FORMATING,
    RIL_CDMA_SMS_UDH_PRE_DEF_SOUND,
    RIL_CDMA_SMS_UDH_USER_DEF_SOUND,
    RIL_CDMA_SMS_UDH_PRE_DEF_ANIM,
    RIL_CDMA_SMS_UDH_LARGE_ANIM,
    RIL_CDMA_SMS_UDH_SMALL_ANIM,
    RIL_CDMA_SMS_UDH_LARGE_PICTURE,
    RIL_CDMA_SMS_UDH_SMALL_PICTURE,
    RIL_CDMA_SMS_UDH_VAR_PICTURE,

    RIL_CDMA_SMS_UDH_USER_PROMPT      = 0x13,
    RIL_CDMA_SMS_UDH_EXTENDED_OBJECT  = 0x14,

    /* 15 - 1F    Reserved for future EMS */

    RIL_CDMA_SMS_UDH_RFC822           = 0x20,

    /*  21 - 6F    Reserved for future use */
    /*  70 - 7f    Reserved for (U)SIM Toolkit Security Headers */
    /*  80 - 9F    SME to SME specific use */
    /*  A0 - BF    Reserved for future use */
    /*  C0 - DF    SC specific use */
    /*  E0 - FF    Reserved for future use */

    RIL_CDMA_SMS_UDH_OTHER            = 0xFFFF, /* For unsupported or proprietary headers */
    RIL_CDMA_SMS_UDH_ID_MAX32 = 0x10000000   /* Force constant ENUM size in structures */

} RIL_CDMA_SMS_UdhId;

typedef struct {
    /*indicates the reference number for a particular concatenated short message. */
    /*it is constant for every short message which makes up a particular concatenated short message*/
    unsigned char       msg_ref;

    /*indicates the total number of short messages within the concatenated short message.
     The value shall start at 1 and remain constant for every
     short message which makes up the concatenated short message.
     if it is 0 then the receiving entity shall ignore the whole Information Element*/
    unsigned char       total_sm;

    /*
     * it indicates the sequence number of a particular short message within the concatenated short
     * message. The value shall start at 1 and increment by one for every short message sent
     * within the concatenated short message. If the value is zero or the value is
     * greater than the value in octet 2 then the receiving
     * entity shall ignore the whole Information Element.
     */
    unsigned char      seq_num;
} RIL_CDMA_SMS_UdhConcat8;

/* GW message waiting actions
*/
typedef enum {
    RIL_CDMA_SMS_GW_MSG_WAITING_NONE,
    RIL_CDMA_SMS_GW_MSG_WAITING_DISCARD,
    RIL_CDMA_SMS_GW_MSG_WAITING_STORE,
    RIL_CDMA_SMS_GW_MSG_WAITING_NONE_1111,
    RIL_CDMA_SMS_GW_MSG_WAITING_MAX32 = 0x10000000 /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_GWMsgWaiting;

/* GW message waiting types
*/
typedef enum {
    RIL_CDMA_SMS_GW_MSG_WAITING_VOICEMAIL,
    RIL_CDMA_SMS_GW_MSG_WAITING_FAX,
    RIL_CDMA_SMS_GW_MSG_WAITING_EMAIL,
    RIL_CDMA_SMS_GW_MSG_WAITING_OTHER,
    RIL_CDMA_SMS_GW_MSG_WAITING_KIND_MAX32 = 0x10000000   /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_GWMsgWaitingKind;

typedef struct {
    RIL_CDMA_SMS_GWMsgWaiting                 msg_waiting;
    RIL_CDMA_SMS_GWMsgWaitingKind             msg_waiting_kind;

    /*it indicates the number of messages of the type specified in Octet 1 waiting.*/
    unsigned char                             message_count;
} RIL_CDMA_SMS_UdhSpecialSM;

typedef struct {
    unsigned char  dest_port;
    unsigned char  orig_port;
} RIL_CDMA_SMS_UdhWap8;

typedef struct {
    unsigned short  dest_port;
    unsigned short  orig_port;
} RIL_CDMA_SMS_UdhWap16;

typedef struct {
    unsigned short      msg_ref;
    unsigned char       total_sm;
    unsigned char       seq_num;

} RIL_CDMA_SMS_UdhConcat16;

typedef enum {
    RIL_CDMA_SMS_UDH_LEFT_ALIGNMENT = 0,
    RIL_CDMA_SMS_UDH_CENTER_ALIGNMENT,
    RIL_CDMA_SMS_UDH_RIGHT_ALIGNMENT,
    RIL_CDMA_SMS_UDH_DEFAULT_ALIGNMENT,
    RIL_CDMA_SMS_UDH_MAX_ALIGNMENT,
    RIL_CDMA_SMS_UDH_ALIGNMENT_MAX32 = 0x10000000   /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_UdhAlignment;

typedef enum {
    RIL_CDMA_SMS_UDH_FONT_NORMAL = 0,
    RIL_CDMA_SMS_UDH_FONT_LARGE,
    RIL_CDMA_SMS_UDH_FONT_SMALL,
    RIL_CDMA_SMS_UDH_FONT_RESERVED,
    RIL_CDMA_SMS_UDH_FONT_MAX,
    RIL_CDMA_SMS_UDH_FONT_MAX32 = 0x10000000   /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_UdhFontSize;

typedef enum {
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BLACK          = 0x0,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_GREY      = 0x1,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_RED       = 0x2,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_YELLOW    = 0x3,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_GREEN     = 0x4,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_CYAN      = 0x5,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_BLUE      = 0x6,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_DARK_MAGENTA   = 0x7,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_GREY           = 0x8,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_WHITE          = 0x9,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_RED     = 0xA,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_YELLOW  = 0xB,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_GREEN   = 0xC,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_CYAN    = 0xD,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_BLUE    = 0xE,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_BRIGHT_MAGENTA = 0xF,
    RIL_CDMA_SMS_UDH_TEXT_COLOR_MAX32 = 0x10000000   /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_UdhTextColor;

typedef struct {
    unsigned char              start_position;
    unsigned char              text_formatting_length;
    RIL_CDMA_SMS_UdhAlignment  alignment_type ;       /*bit 0 and  bit 1*/
    RIL_CDMA_SMS_UdhFontSize   font_size ;            /*bit 3 and  bit 2*/
    unsigned char              style_bold;            /*bit 4 */
    unsigned char              style_italic;          /*bit 5  */
    unsigned char              style_underlined;      /*bit 6 */
    unsigned char              style_strikethrough;   /*bit 7 */

    /* if FALSE, ignore the following color information */
    unsigned char              is_color_present;
    RIL_CDMA_SMS_UdhTextColor  text_color_foreground;
    RIL_CDMA_SMS_UdhTextColor  text_color_background;

} RIL_CDMA_SMS_UdhTextFormating;

/* Predefined sound
*/
typedef struct {
    unsigned char       position;
    unsigned char       snd_number;
} RIL_CDMA_SMS_UdhPreDefSound;

/* User Defined sound
*/
typedef struct {
    unsigned char       data_length;
    unsigned char       position;
    unsigned char       user_def_sound[RIL_CDMA_SMS_UDH_MAX_SND_SIZE];
} RIL_CDMA_SMS_UdhUserDefSound;

/* Large picture
*/
typedef struct {
    unsigned char       position;
    unsigned char       data[RIL_CDMA_SMS_UDH_LARGE_PIC_SIZE];
} RIL_CDMA_SMS_UdhLargePictureData;

/* Small picture
*/
typedef struct {
    unsigned char       position;
    unsigned char       data[RIL_CDMA_SMS_UDH_SMALL_PIC_SIZE];
} RIL_CDMA_SMS_UdhSmallPictureData;

/* Variable length picture
*/
typedef struct {
    unsigned char       position;
    unsigned char       width;    /* Number of pixels - Should be a mutliple of 8 */
    unsigned char       height;
    unsigned char       data[RIL_CDMA_SMS_UDH_VAR_PIC_SIZE];
} RIL_CDMA_SMS_UdhVarPicture;

/* Predefined animation
*/
typedef struct {
    unsigned char       position;
    unsigned char       animation_number;
} RIL_CDMA_SMS_UdhPreDefAnim;

/* Large animation
*/
typedef struct {
    unsigned char       position;
    unsigned char       data[RIL_CDMA_SMS_UDH_ANIM_NUM_BITMAPS][RIL_CDMA_SMS_UDH_LARGE_BITMAP_SIZE];
} RIL_CDMA_SMS_UdhLargeAnim;

/* Small animation
*/
typedef struct {
    unsigned char       position;
    unsigned char       data[RIL_CDMA_SMS_UDH_ANIM_NUM_BITMAPS][RIL_CDMA_SMS_UDH_SMALL_BITMAP_SIZE];
} RIL_CDMA_SMS_UdhSmallAnim;

/* User Prompt Indicator UDH
*/
typedef struct {
    unsigned char       number_of_objects;
    /* Number of objects of the same kind that follow this header which will
    ** be stitched together by the applications. For example, 5 small pictures
    ** are to be stitched together horizontally, or 6 iMelody tones are to be
    ** connected together with intermediate iMelody header and footer ignored.
    ** Allowed objects to be stitched:
    **   - Images (small, large, variable)
    **   - User defined sounds
    */
} RIL_CDMA_SMS_UdhUserPrompt;

typedef struct {
    unsigned char         length;

    unsigned char         data[RIL_CDMA_SMS_UDH_EO_DATA_SEGMENT_MAX];
    /* RIL_CDMA_SMS_UDH_EO_VCARD: See http://www.imc.org/pdi/vcard-21.doc for payload */
    /* RIL_CDMA_SMS_UDH_EO_VCALENDAR: See http://www.imc.org/pdi/vcal-10.doc */
    /* Or: Unsupported/proprietary extended objects */

} RIL_CDMA_SMS_UdhEoContent;

/* Extended Object UDH
*/
/* Extended Object IDs/types
*/
typedef enum {
    RIL_CDMA_SMS_UDH_EO_VCARD                   = 0x09,
    RIL_CDMA_SMS_UDH_EO_VCALENDAR               = 0x0A,
    RIL_CDMA_SMS_UDH_EO_MAX32 = 0x10000000   /* Force constant ENUM size in structures */
} RIL_CDMA_SMS_UdhEoId;

typedef struct {
    /* Extended objects are to be used together with 16-bit concatenation
    ** UDH. The max number of segments supported for E.O. is 8 at least.
    */
    RIL_CDMA_SMS_UdhEoContent    content;

    unsigned char                                 first_segment;
    /* The following fields are only present in the first segment of a
    ** concatenated SMS message.
    */
   unsigned char                                   reference;
    /* Identify those extended object segments which should be linked together
    */
   unsigned short                                  length;
    /* Length of the whole extended object data
    */
    unsigned char                                   control;
    RIL_CDMA_SMS_UdhEoId                    type;
    unsigned short                                  position;
    /* Absolute position of the E.O. in the whole text after concatenation,
    ** starting from 1.
    */
} RIL_CDMA_SMS_UdhEo;

typedef struct {
    RIL_CDMA_SMS_UdhId  header_id;
    unsigned char               header_length;
    unsigned char              data[RIL_CDMA_SMS_UDH_OTHER_SIZE];
} RIL_CDMA_SMS_UdhOther;

typedef struct {
    unsigned char        header_length;
} RIL_CDMA_SMS_UdhRfc822;

typedef struct {
    RIL_CDMA_SMS_UdhId                header_id;

    union {
        RIL_CDMA_SMS_UdhConcat8             concat_8;       // 00

        RIL_CDMA_SMS_UdhSpecialSM           special_sm;     // 01
        RIL_CDMA_SMS_UdhWap8                wap_8;          // 04
        RIL_CDMA_SMS_UdhWap16               wap_16;         // 05
        RIL_CDMA_SMS_UdhConcat16            concat_16;      // 08
        RIL_CDMA_SMS_UdhTextFormating       text_formating; // 0a
        RIL_CDMA_SMS_UdhPreDefSound         pre_def_sound;  // 0b
        RIL_CDMA_SMS_UdhUserDefSound        user_def_sound; // 0c
        RIL_CDMA_SMS_UdhPreDefAnim          pre_def_anim;   // 0d
        RIL_CDMA_SMS_UdhLargeAnim           large_anim;     // 0e
        RIL_CDMA_SMS_UdhSmallAnim           small_anim;     // 0f
        RIL_CDMA_SMS_UdhLargePictureData    large_picture;  // 10
        RIL_CDMA_SMS_UdhSmallPictureData    small_picture;  // 11
        RIL_CDMA_SMS_UdhVarPicture          var_picture;    // 12

        RIL_CDMA_SMS_UdhUserPrompt          user_prompt;    // 13
        RIL_CDMA_SMS_UdhEo                  eo;             // 14

        RIL_CDMA_SMS_UdhRfc822              rfc822;         // 20
        RIL_CDMA_SMS_UdhOther               other;

    }u;
} RIL_CDMA_SMS_Udh;

/* ----------------------------- */
/* -- User data encoding type -- */
/* ----------------------------- */
typedef enum {
    RIL_CDMA_SMS_ENCODING_OCTET        = 0,    /* 8-bit */
    RIL_CDMA_SMS_ENCODING_IS91EP,              /* varies */
    RIL_CDMA_SMS_ENCODING_ASCII,               /* 7-bit */
    RIL_CDMA_SMS_ENCODING_IA5,                 /* 7-bit */
    RIL_CDMA_SMS_ENCODING_UNICODE,             /* 16-bit */
    RIL_CDMA_SMS_ENCODING_SHIFT_JIS,           /* 8 or 16-bit */
    RIL_CDMA_SMS_ENCODING_KOREAN,              /* 8 or 16-bit */
    RIL_CDMA_SMS_ENCODING_LATIN_HEBREW,        /* 8-bit */
    RIL_CDMA_SMS_ENCODING_LATIN,               /* 8-bit */
    RIL_CDMA_SMS_ENCODING_GSM_7_BIT_DEFAULT,   /* 7-bit */
    RIL_CDMA_SMS_ENCODING_MAX32        = 0x10000000

} RIL_CDMA_SMS_UserDataEncoding;

/* ------------------------ */
/* -- IS-91 EP data type -- */
/* ------------------------ */
typedef enum {
    RIL_CDMA_SMS_IS91EP_VOICE_MAIL         = 0x82,
    RIL_CDMA_SMS_IS91EP_SHORT_MESSAGE_FULL = 0x83,
    RIL_CDMA_SMS_IS91EP_CLI_ORDER          = 0x84,
    RIL_CDMA_SMS_IS91EP_SHORT_MESSAGE      = 0x85,
    RIL_CDMA_SMS_IS91EP_MAX32              = 0x10000000

} RIL_CDMA_SMS_IS91EPType;

typedef struct {
    /* NOTE: If message_id.udh_present == TRUE:
    **       'num_headers' is the number of User Data Headers (UDHs),
    **       and 'headers' include all those headers.
    */
    unsigned char                              num_headers;
    RIL_CDMA_SMS_Udh                     headers[RIL_CDMA_SMS_MAX_UD_HEADERS];

    RIL_CDMA_SMS_UserDataEncoding      encoding;
    RIL_CDMA_SMS_IS91EPType             is91ep_type;

    /*----------------------------------------------------------------------
     'data_len' indicates the valid number of bytes in the 'data' array.

     'padding_bits' (0-7) indicates how many bits in the last byte of 'data'
     are invalid bits. This parameter is only used for Mobile-Originated
     messages. There is no way for the API to tell how many padding bits
     exist in the received message. Instead, the application can find out how
     many padding bits exist in the user data when decoding the user data.

     'data' has the raw bits of the user data field of the SMS message.
     The client software should decode the raw user data according to its
     supported encoding types and languages.

     EXCEPTION 1: CMT-91 user data raw bits are first translated into BD fields
     (e.g. num_messages, callback, etc.) The translated user data field in
     VMN and Short Message is in the form of ASCII characters, each occupying
     a byte in the resulted 'data'.

     EXCEPTION 2: GSM 7-bit Default characters are decoded so that each byte
     has one 7-bit GSM character.

     'number_of_digits' is the number of digits/characters (7, 8, 16, or
     whatever bits) in the raw user data, which can be used by the client
     when decoding the user data according to the encoding type and language.
    -------------------------------------------------------------------------*/
    unsigned char                                data_len;
    unsigned char                                padding_bits;
    unsigned char                                data[ RIL_CDMA_SMS_USER_DATA_MAX ];
    unsigned char                                number_of_digits;

} RIL_CDMA_SMS_CdmaUserData;

/* -------------------- */
/* ---- Message Id ---- */
/* -------------------- */
typedef enum {
    RIL_CDMA_SMS_BD_TYPE_RESERVED_0     = 0,
    RIL_CDMA_SMS_BD_TYPE_DELIVER,       /* MT only */
    RIL_CDMA_SMS_BD_TYPE_SUBMIT,        /* MO only */
    RIL_CDMA_SMS_BD_TYPE_CANCELLATION,  /* MO only */
    RIL_CDMA_SMS_BD_TYPE_DELIVERY_ACK,  /* MT only */
    RIL_CDMA_SMS_BD_TYPE_USER_ACK,      /* MT & MO */
    RIL_CDMA_SMS_BD_TYPE_READ_ACK,      /* MT & MO */
    RIL_CDMA_SMS_BD_TYPE_MAX32          = 0x10000000

} RIL_CDMA_SMS_BdMessageType;

typedef unsigned int  RIL_CDMA_SMS_MessageNumber;

typedef struct {
    RIL_CDMA_SMS_BdMessageType   type;
    RIL_CDMA_SMS_MessageNumber      id_number;
    unsigned char                      udh_present;
    /* NOTE: if FEATURE_SMS_UDH is not defined,
    ** udh_present should be ignored.
    */
} RIL_CDMA_SMS_MessageId;

typedef unsigned char           RIL_CDMA_SMS_UserResponse;

/* ------------------- */
/* ---- Timestamp ---- */
/* ------------------- */
typedef struct {
    /* If 'year' is between 96 and 99, the actual year is 1900 + 'year';
       if 'year' is between 00 and 95, the actual year is 2000 + 'year'.
       NOTE: Each field has two BCD digits and byte arrangement is <MSB, ... ,LSB>
    */
    unsigned char      year;        /* 0x00-0x99 */
    unsigned char      month;       /* 0x01-0x12 */
    unsigned char      day;         /* 0x01-0x31 */
    unsigned char      hour;        /* 0x00-0x23 */
    unsigned char      minute;      /* 0x00-0x59 */
    unsigned char      second;      /* 0x00-0x59 */
    signed char      timezone;    /* +/-, [-48,+48] number of 15 minutes - GW only */
} RIL_CDMA_SMS_Timestamp;

/* ------------------ */
/* ---- Priority ---- */
/* ------------------ */
typedef enum {
    RIL_CDMA_SMS_PRIORITY_NORMAL      = 0,
    RIL_CDMA_SMS_PRIORITY_INTERACTIVE,
    RIL_CDMA_SMS_PRIORITY_URGENT,
    RIL_CDMA_SMS_PRIORITY_EMERGENCY,
    RIL_CDMA_SMS_PRIORITY_MAX32       = 0x10000000

} RIL_CDMA_SMS_Priority;

/* ----------------- */
/* ---- Privacy ---- */
/* ----------------- */
typedef enum {
    RIL_CDMA_SMS_PRIVACY_NORMAL      = 0,
    RIL_CDMA_SMS_PRIVACY_RESTRICTED,
    RIL_CDMA_SMS_PRIVACY_CONFIDENTIAL,
    RIL_CDMA_SMS_PRIVACY_SECRET,
    RIL_CDMA_SMS_PRIVACY_MAX32       = 0x10000000

} RIL_CDMA_SMS_Privacy;

/* ---------------------- */
/* ---- Reply option ---- */
/* ---------------------- */
typedef struct {
    /* whether user ack is requested
    */
    unsigned char          user_ack_requested;

    /* whether delivery ack is requested.
       Should be FALSE for incoming messages.
    */
    unsigned char          delivery_ack_requested;

    /* Message originator requests the receiving phone to send back a READ_ACK
    ** message automatically when the user reads the received message.
    */
    unsigned char          read_ack_requested;

} RIL_CDMA_SMS_ReplyOption;

typedef enum {
    RIL_CDMA_SMS_ALERT_MODE_DEFAULT         = 0,
    RIL_CDMA_SMS_ALERT_MODE_LOW_PRIORITY    = 1,
    RIL_CDMA_SMS_ALERT_MODE_MEDIUM_PRIORITY = 2,
    RIL_CDMA_SMS_ALERT_MODE_HIGH_PRIORITY   = 3,

    /* For pre-IS637A implementations, alert_mode only has values of True/False:
    */
    RIL_CDMA_SMS_ALERT_MODE_OFF   = 0,
    RIL_CDMA_SMS_ALERT_MODE_ON    = 1

} RIL_CDMA_SMS_AlertMode;

/* ------------------ */
/* ---- Language ---- */
/* ------------------ */
typedef enum {
    RIL_CDMA_SMS_LANGUAGE_UNSPECIFIED = 0,
    RIL_CDMA_SMS_LANGUAGE_ENGLISH,
    RIL_CDMA_SMS_LANGUAGE_FRENCH,
    RIL_CDMA_SMS_LANGUAGE_SPANISH,
    RIL_CDMA_SMS_LANGUAGE_JAPANESE,
    RIL_CDMA_SMS_LANGUAGE_KOREAN,
    RIL_CDMA_SMS_LANGUAGE_CHINESE,
    RIL_CDMA_SMS_LANGUAGE_HEBREW,
    RIL_CDMA_SMS_LANGUAGE_MAX32       = 0x10000000

} RIL_CDMA_SMS_Language;

/* ---------------------------------- */
/* ---------- Display Mode ---------- */
/* ---------------------------------- */
typedef enum {
    RIL_CDMA_SMS_DISPLAY_MODE_IMMEDIATE   = 0,
    RIL_CDMA_SMS_DISPLAY_MODE_DEFAULT     = 1,
    RIL_CDMA_SMS_DISPLAY_MODE_USER_INVOKE = 2,
    RIL_CDMA_SMS_DISPLAY_MODE_RESERVED    = 3
} RIL_CDMA_SMS_DisplayMode;

/* IS-637B parameters/fields
*/

/* ---------------------------------- */
/* ---------- Delivery Status ------- */
/* ---------------------------------- */
typedef enum {
    RIL_CDMA_SMS_DELIVERY_STATUS_ACCEPTED              = 0,    /* ERROR_CLASS_NONE */
    RIL_CDMA_SMS_DELIVERY_STATUS_DEPOSITED_TO_INTERNET = 1,    /* ERROR_CLASS_NONE */
    RIL_CDMA_SMS_DELIVERY_STATUS_DELIVERED             = 2,    /* ERROR_CLASS_NONE */
    RIL_CDMA_SMS_DELIVERY_STATUS_CANCELLED             = 3,    /* ERROR_CLASS_NONE */

    RIL_CDMA_SMS_DELIVERY_STATUS_NETWORK_CONGESTION  = 4,    /* ERROR_CLASS_TEMP & PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_NETWORK_ERROR       = 5,    /* ERROR_CLASS_TEMP & PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_CANCEL_FAILED       = 6,    /* ERROR_CLASS_PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_BLOCKED_DESTINATION = 7,    /* ERROR_CLASS_PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_TEXT_TOO_LONG       = 8,    /* ERROR_CLASS_PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_DUPLICATE_MESSAGE   = 9,    /* ERROR_CLASS_PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_INVALID_DESTINATION = 10,   /* ERROR_CLASS_PERM */
    RIL_CDMA_SMS_DELIVERY_STATUS_MESSAGE_EXPIRED     = 13,   /* ERROR_CLASS_PERM */

    RIL_CDMA_SMS_DELIVERY_STATUS_UNKNOWN_ERROR       = 0x1F  /* ERROR_CLASS_PERM */

    /* All the other values are reserved */

} RIL_CDMA_SMS_DeliveryStatusE;

typedef struct {
    RIL_CDMA_SMS_ErrorClass       error_class;
    RIL_CDMA_SMS_DeliveryStatusE   status;
} RIL_CDMA_SMS_DeliveryStatus;

typedef struct {
    unsigned char               address[RIL_CDMA_SMS_IP_ADDRESS_SIZE];
    unsigned char             is_valid;
} RIL_CDMA_SMS_IpAddress;

/* This special parameter captures any unrecognized/proprietary parameters
*/
typedef struct {
    unsigned char                         input_other_len;
    unsigned char                         desired_other_len; /* used during decoding */
    unsigned char                         * other_data;
} RIL_CDMA_SMS_OtherParm;

typedef struct {
    /* the mask indicates which fields are present in this message */
    unsigned int                        mask;

    RIL_CDMA_SMS_MessageId         message_id;
    RIL_CDMA_SMS_CdmaUserData     user_data;
    RIL_CDMA_SMS_UserResponse        user_response;
    RIL_CDMA_SMS_Timestamp          mc_time;
    RIL_CDMA_SMS_Timestamp          validity_absolute;
    RIL_CDMA_SMS_Timestamp          validity_relative;
    RIL_CDMA_SMS_Timestamp          deferred_absolute;
    RIL_CDMA_SMS_Timestamp          deferred_relative;
    RIL_CDMA_SMS_Priority           priority;
    RIL_CDMA_SMS_Privacy            privacy;
    RIL_CDMA_SMS_ReplyOption       reply_option;
    unsigned char                         num_messages;  /* the actual value; not BCDs */
    RIL_CDMA_SMS_AlertMode         alert_mode;
     /* For pre-IS-637A implementations, alert_mode is either Off or On. */
    RIL_CDMA_SMS_Language           language;
    RIL_CDMA_SMS_Address            callback;
    RIL_CDMA_SMS_DisplayMode       display_mode;

    RIL_CDMA_SMS_DeliveryStatus    delivery_status;
    unsigned int                        deposit_index;

    RIL_CDMA_SMS_IpAddress         ip_address;
    unsigned char                         rsn_no_notify;

    /* See function comments of wms_ts_decode() and
    ** wms_ts_decode_cdma_bd_with_other() for details regarding 'other' parameters
    */
    RIL_CDMA_SMS_OtherParm         other;

} RIL_CDMA_SMS_ClientBd;

typedef struct {
    unsigned char length;   /* length, in bytes, of the encoded SMS message */
    unsigned char * data;   /* the encoded SMS message (max 255 bytes) */
} RIL_CDMA_Encoded_SMS;

#ifdef __cplusplus
}
#endif

#endif /*ANDROID_RIL_CDMA_SMS_H*/
