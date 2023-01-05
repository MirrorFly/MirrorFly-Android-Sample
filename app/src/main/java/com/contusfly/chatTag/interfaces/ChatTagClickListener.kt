package com.contusfly.chatTag.interfaces

import com.contusflysdk.api.models.RecentChat

interface ChatTagClickListener {

    fun selectUnselectChat(position:Int,item:RecentChat,isSelectionlist:Boolean)
    fun filterListUpdated(filterList:ArrayList<RecentChat>)
}
