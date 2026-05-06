package fr.faboit.blg.dialog;

/**
 * Holds the transient UI state for one player.
 *
 * Passwords are kept as plain {@link String} fields only for the duration
 * of the dialog session and cleared immediately after the command is dispatched.
 *
 * This plugin NEVER persists or logs passwords.
 */
public final class PlayerDialogState {

    private DialogType currentDialog = DialogType.NONE;

    /** Temporarily stores the password entered in the current session. */
    private String password = "";

    /** Temporarily stores the confirmation password in the register session. */
    private String confirmation = "";

    /** The last error message intercepted from chat. */
    private String lastError = "";

    /**
     * The dialog context from which an error was triggered
     * (LOGIN or REGISTER) so the Error dialog knows which "Back" button to show.
     */
    private DialogType errorContext = DialogType.NONE;

    // ── Accessors ─────────────────────────────────────────────────────────────

    public DialogType getCurrentDialog()  { return currentDialog; }
    public void setCurrentDialog(DialogType type) { this.currentDialog = type; }

    public boolean hasPassword()    { return password != null && !password.isEmpty(); }
    public String  getPassword()    { return password; }
    public void    setPassword(String pw) { this.password = pw; }

    public boolean hasConfirmation()  { return confirmation != null && !confirmation.isEmpty(); }
    public String  getConfirmation()  { return confirmation; }
    public void    setConfirmation(String c) { this.confirmation = c; }

    public String    getLastError()   { return lastError; }
    public void      setLastError(String e) { this.lastError = e; }

    public DialogType getErrorContext()  { return errorContext; }
    public void       setErrorContext(DialogType ctx) { this.errorContext = ctx; }

    public boolean isInDialog() { return currentDialog != DialogType.NONE; }

    /**
     * Clears all sensitive credential data.
     * Called after a command is dispatched or the dialog is cancelled.
     */
    public void clearCredentials() {
        // Overwrite with empty string to minimise time the value is in memory
        this.password     = "";
        this.confirmation = "";
    }

    /** Full reset – call when all dialogs close. */
    public void reset() {
        clearCredentials();
        this.currentDialog = DialogType.NONE;
        this.lastError     = "";
        this.errorContext  = DialogType.NONE;
    }
}
