package com.handrail.actions

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [IrreversibleActionGuard] is the CODE-level hard stop that
 * [ActionExecutor.tap] checks before performing any tap — and therefore what
 * both [com.handrail.agent.AgentLoop] (Takeover mode) and
 * [com.handrail.ui.HandrailInteractionSession] (the invoke sheet's Working
 * state) actually rely on to guarantee the loop halts before any payment,
 * send, submit, transfer, order, confirm, or purchase action reaches the
 * screen. Per CLAUDE.md: "This is not a setting and cannot be disabled ...
 * write the test for it first." This is that test.
 */
class IrreversibleActionGuardTest {

    @Test
    fun `blocks a visible Pay button`() {
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Pay ₹450"))
    }

    @Test
    fun `blocks send, confirm, order, submit, transfer and buy`() {
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Send message"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Confirm booking"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Place order"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Submit application"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Transfer funds"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("Buy now"))
    }

    @Test
    fun `blocks Hindi and Kannada equivalents`() {
        assertNotNull(IrreversibleActionGuard.matchedKeyword("भुगतान करें"))
        assertNotNull(IrreversibleActionGuard.matchedKeyword("ಪಾವತಿ ಮಾಡಿ"))
    }

    @Test
    fun `does not block ordinary navigation text`() {
        assertNull(IrreversibleActionGuard.matchedKeyword("Open Settings"))
        assertNull(IrreversibleActionGuard.matchedKeyword("Wi-Fi"))
        assertNull(IrreversibleActionGuard.matchedKeyword(""))
    }

    @Test
    fun `does not false-positive on word fragments`() {
        // Regression per IrreversibleActionGuard's own doc: a plain `contains`
        // check used to match "pay" inside "Paytm" and "Repayment", which
        // would have blocked tapping the Paytm app icon or a "Repayment
        // history" row — neither of which is itself an irreversible action.
        assertNull(IrreversibleActionGuard.matchedKeyword("Paytm"))
        assertNull(IrreversibleActionGuard.matchedKeyword("Repayment history"))
    }
}
