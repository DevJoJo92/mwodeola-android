package com.jojo.android.mwodeola.presentation.security.screen

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jojo.android.mwodeola.R
import com.jojo.android.mwodeola.databinding.ActivityAuthenticationBinding
import com.jojo.android.mwodeola.presentation.BaseActivity
import com.jojo.android.mwodeola.presentation.common.BottomUpDialog
import com.jojo.android.mwodeola.presentation.security.AuthType
import com.jojo.android.mwodeola.util.Compat

abstract class BaseAuthenticationActivity : BaseActivity(), AuthenticationContract.View {

    companion object {
        private const val TAG = "BaseAuthenticationActivity"

        const val EXTRA_PURPOSE = "purpose"
        const val EXTRA_RESULT = "auth_result"
        const val EXTRA_AUTH_TYPE = "auth_type"
        const val EXTRA_USER_NAME = "user_name"
        const val EXTRA_USER_EMAIL = "user_email"
        const val EXTRA_USER_PHONE_NUMBER = "user_phone_number"

        const val PURPOSE_AUTH = 0
        const val PURPOSE_SCREEN_LOCK = 1
        const val PURPOSE_SIGN_IN = 2
        const val PURPOSE_SIGN_UP = 3
        const val PURPOSE_CREATE = 4
        const val PURPOSE_CHANGE = 5
        const val PURPOSE_DELETE = 6

        const val SUCCEED = 0
        const val CANCELLED = 1
        const val EXCEEDED_AUTH_LIMIT = 2
    }

    interface BackPressedInterceptor {
        fun onInterceptBackPressed(): Boolean
    }

    abstract val presenter: AuthenticationContract.Presenter
    abstract val authType: AuthType
    private var _purpose = PURPOSE_AUTH

    override val isScreenLockEnabled: Boolean = false
    override val binding by lazy { ActivityAuthenticationBinding.inflate(layoutInflater) }

    private val backPressedInterceptorMap = hashMapOf<Int, BackPressedInterceptor>()

    protected val biometricConfirmedOkTintColor
            by lazy { ColorStateList.valueOf(resources.getColor(R.color.green500, null)) }
    protected val biometricConfirmedNoTintColor
            by lazy { ColorStateList.valueOf(resources.getColor(R.color.white, null)) }

    protected val currentPage: Int
        get() = binding.viewPager2.currentItem
    protected val currentFragment: BaseAuthenticationFragment
        get() = supportFragmentManager.fragments[currentPage] as BaseAuthenticationFragment
    protected val lastPage: Int
        get() = binding.viewPager2.adapter!!.itemCount - 1
    protected val pageCount: Int
        get() = binding.viewPager2.adapter!!.itemCount

    val purpose: Int
        get() = _purpose

    protected var isBiometricConfirmed = false

    protected val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        presenter.loadAuthFailureCount()
        presenter.checkBiometricEnroll()

        intent.let {
            _purpose = it.getIntExtra(EXTRA_PURPOSE, PURPOSE_AUTH)

            presenter.userName = it.getStringExtra(EXTRA_USER_NAME)
            presenter.userEmail = it.getStringExtra(EXTRA_USER_EMAIL)
            presenter.userPhoneNumber = it.getStringExtra(EXTRA_USER_PHONE_NUMBER)
        }

        resultIntent.run {
            putExtra(EXTRA_PURPOSE, purpose)
            putExtra(EXTRA_AUTH_TYPE, authType)
            putExtra(EXTRA_RESULT, CANCELLED)
        }

        setResult(RESULT_CANCELED, resultIntent)

        initWindowProperties()
        initView()
    }

    protected fun initView(): Unit = with(binding) {
        btnBack.setOnClickListener { onBackPressed() }
        tvHeader.text = when (purpose) {
            PURPOSE_SCREEN_LOCK -> "?????? ??????"
            PURPOSE_AUTH -> "???????????? ??????"
            PURPOSE_SIGN_IN -> "?????????"
            PURPOSE_SIGN_UP,
            PURPOSE_CREATE -> "??? ???????????? ??????"
            PURPOSE_CHANGE -> "???????????? ??????"
            PURPOSE_DELETE -> "???????????? ??????"
            else -> ""
        }

        viewPager2.isUserInputEnabled = false
        viewPager2.adapter = ViewPagerAdapter()
    }

    override fun onBackPressed() {
        val isIntercept = backPressedInterceptorMap[currentPage]
            ?.onInterceptBackPressed() ?: false

        if (!isIntercept) {
            showExitConfirmDialog()
        }
    }

    override fun showBiometricConfirmButton() {
        Log.e(TAG, "showBiometricConfirmButton()")
        binding.btnBiometricAuthConfirmed.visibility = View.VISIBLE
        binding.btnBiometricAuthConfirmed.setOnClickListener {
            isBiometricConfirmed = !isBiometricConfirmed
            updateBiometricConfirmButton()
        }
    }

    override fun showBiometricEnrollChangedDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("?????? ?????? ?????? ?????????")
            .subtitle("???????????? ?????? ?????? ????????? ???????????? ?????? ??????????????? ?????? ????????? ???????????????.")
            .confirmedButton()
            .show()
    }

    override fun showCompletelySignUpDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("?????? ?????? :)")
            .subtitle("???????????? ????????? ???????????????")
            .confirmedButton {
                finishForSucceed()
            }
            .show()
    }

    override fun showCompletelyCreationDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("??????????????? ??????????????? ?????????????????????")
            .confirmedButton {
                finishForSucceed()
            }
            .show()
    }

    override fun showCompletelyChangeDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("??????????????? ??????????????? ?????????????????????")
            .confirmedButton {
                finishForSucceed()
            }
            .show()
    }

    override fun showCompletelyDeletionDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("??????????????? ??????????????? ?????????????????????")
            .confirmedButton {
                finishForSucceed()
            }
            .show()
    }

    override fun showAuthenticationExceededDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("???????????? ?????? ?????? ????????? ???????????? ????????? ?????? ???????????????")
            .subtitle("?????? ?????? ??????: ${presenter.authFailureLimit}???")
            .confirmedButton {
                finishForAuthenticationExceeded()
            }
            .show()
    }

    override fun showDormantUserDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("?????? ??????")
            .subtitle("????????? ??????????????? ?????? ?????? ????????? ?????? ???????????????.")
            .confirmedButton {
                finishForFailure()
            }
            .show()
    }

    override fun showLockedUserDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("?????? ??????")
            .subtitle("???????????? ?????? ?????? ?????? ?????? ????????? ????????? ?????? ????????????.")
            .confirmedButton {
                finishForFailure()
            }
            .show()
    }

    override fun showNotFoundUserDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("????????? ?????? ??? ??????")
            .subtitle("???????????? ????????? ???????????? ?????? ??? ????????????. ?????? ????????? ?????????.")
            .confirmedButton {
                finishForFailure()
            }
            .show()
    }

    override fun finishForSucceed() {
        if (isBiometricConfirmed) {
            presenter.changeAuthTypeToBiometric()
        }
        resultIntent.putExtra(EXTRA_RESULT, SUCCEED)
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun finishForFailure() {
        resultIntent.putExtra(EXTRA_RESULT, CANCELLED)
        setResult(RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun finishForAuthenticationExceeded() {
        resultIntent.putExtra(EXTRA_RESULT, EXCEEDED_AUTH_LIMIT)
        setResult(RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun finish() {
        Log.d(TAG, "finish()")
        super.finish()
    }

    fun moveBackPage() {
        if (currentPage > 0) {
            binding.viewPager2.currentItem -= 1
        }
    }

    fun moveNextPage() {
        if (currentPage < lastPage) {
            binding.viewPager2.currentItem += 1
        }
    }

    abstract fun authenticationFragment(): BaseAuthenticationFragment
    abstract fun passwordInputFragment(): BaseAuthenticationFragment
    abstract fun passwordInputConfirmFragment(): BaseAuthenticationFragment

    // abstract fun canBackPage(): Boolean

    private fun updateBiometricConfirmButton() {
        val alpha =
            if (isBiometricConfirmed) 1f
            else 0.3f

        val tintColor =
            if (isBiometricConfirmed) biometricConfirmedOkTintColor
            else biometricConfirmedNoTintColor

        binding.btnBiometricAuthConfirmed.alpha = alpha
        binding.btnBiometricAuthConfirmed.iconTint = tintColor
        binding.btnBiometricAuthConfirmed.setTextColor(tintColor)
    }

    private fun showExitConfirmDialog() {
        BottomUpDialog.Builder(supportFragmentManager)
            .title("???????????? ??????????????????????")
            .positiveButton { finish() }
            .show()
    }

    private fun initWindowProperties() {
        window.run {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)

            Compat.setDefaultStatusBar(window)

            statusBarColor = ContextCompat.getColor(
                baseContext, R.color.authentication_screen_background_color)
            navigationBarColor = ContextCompat.getColor(
                baseContext, R.color.authentication_screen_background_color)
        }
    }

    inner class ViewPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = when (purpose) {
            PURPOSE_AUTH,
            PURPOSE_SCREEN_LOCK,
            PURPOSE_SIGN_IN,
            PURPOSE_DELETE -> 1
            PURPOSE_SIGN_UP,
            PURPOSE_CREATE -> 2
            PURPOSE_CHANGE -> 3
            else -> throw IllegalArgumentException("purpose=$purpose")
        }

        override fun createFragment(position: Int): Fragment = when (purpose) {
            PURPOSE_AUTH,
            PURPOSE_SCREEN_LOCK,
            PURPOSE_SIGN_IN,
            PURPOSE_DELETE ->
                authenticationFragment()
            PURPOSE_SIGN_UP,
            PURPOSE_CREATE -> when (position) {
                0 -> passwordInputFragment()
                1 -> passwordInputConfirmFragment()
                else -> throw IllegalArgumentException("purpose=$purpose, position=$position")
            }
            PURPOSE_CHANGE -> when (position) {
                0 -> authenticationFragment()
                1 -> passwordInputFragment()
                2 -> passwordInputConfirmFragment()
                else -> throw IllegalArgumentException("purpose=$purpose, position=$position")
            }
            else -> throw IllegalArgumentException("purpose=$purpose, position=$position")
        }.also { backPressedInterceptorMap[position] = it }
    }
}