package com.contusfly.di.modules

import androidx.lifecycle.ViewModel
import com.contusfly.call.calllog.CallLogViewModel
import com.contusfly.call.groupcall.CallViewModel
import com.contusfly.di.ViewModelKey
import com.contusfly.viewmodels.*
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
@Module
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(DashboardViewModel::class)
    internal abstract fun bindDashboardViewModel(dashboardViewModel: DashboardViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ChatParentViewModel::class)
    internal abstract fun bindChatParentViewModel(chatParentViewModel: ChatParentViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ContactViewModel::class)
    internal abstract fun bindContactViewModel(contactViewModel: ContactViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(RegisterViewModel::class)
    internal abstract fun bindRegisterViewModel(registerViewModel: RegisterViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ChatViewModel::class)
    internal abstract fun bindChatViewModel(chatViewModel: ChatViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ForwardMessageViewModel::class)
    internal abstract fun bindForwardMessageViewModel(chatViewModel: ForwardMessageViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CallLogViewModel::class)
    internal abstract fun bindCallLogViewModel(callLogViewModel: CallLogViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CallViewModel::class)
    internal abstract fun bindCallViewModel(callViewModel: CallViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MediaPreviewViewModel::class)
    internal abstract fun bindMediaPreviewViewModel(mediaPreviewViewModel: MediaPreviewViewModel): ViewModel
}