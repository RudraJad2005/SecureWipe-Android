package com.example.nwipe_android;

public class WipeJob {
    public static final int MAX_NUMBER_PASSES = 10;
    public static final int DEFAULT_NUMBER_PASSES = 3;
    public static final boolean DEFAULT_VERIFY = false;
    public static final boolean DEFAULT_BLANK = true;
    /**
     * Minimum percentage of completion to consider a pass a successfully written.
     */
    public static final int MIN_PERCENTAGE_COMPLETION = 98;

    /**
     * Parameters to the job.
     */
    public int number_passes;
    public boolean verify;
    public boolean blank;

    /**
     * Optional pre-wipe deletion phase.
     * When true, engine will attempt to delete existing user files on accessible storage
     * (e.g., external storage excluding critical system directories) before overwriting.
     */
    public boolean deleteExistingFirst = true;
    public boolean deletionCompleted = false;

    /**
     * Optional: when set, wiping is restricted to this storage root only (e.g., a USB drive path).
     * If null or empty, the engine will wipe the default storage locations as before.
     */
    public String targetPath = null; // absolute directory path to wipe only
    public String targetName = null; // display name for logs/UI

    /**
     * Optional: when set, only the provided folders (and their contents) will be securely wiped.
     * Absolute directory paths. This takes precedence over targetPath if non-empty.
     */
    public java.util.List<String> targetFolders = new java.util.ArrayList<>();

    public boolean hasSelectedFolders() {
        return targetFolders != null && !targetFolders.isEmpty();
    }

    /**
     * Completion status.
     */
    public int passes_completed = 0;
    public String errorMessage = "";

    /**
     * Information on the current pass.
     */
    public long totalBytes;
    public long wipedBytes = 0;
    public boolean verifying = false;

    public String toString() {
        String completionText = String.format(" (%d%%)", this.getCurrentPassPercentageCompletion());

        if (this.failed()) {
            return "Failed" + completionText;
        }

        if (this.isCompleted()) {
            return "Succeeded";
        }

        if (this.isBlankingPass() && this.blank) {
            if (this.verifying) {
                return "Verifying Blanking pass" + completionText;
            } else {
                return "Blanking pass" + completionText;
            }
        }

        if (this.verifying) {
            return String.format(
                    "Verifying Pass %d/%d%s",
                    this.passes_completed + 1,
                    this.number_passes,
                    completionText
            );
        }
        return String.format("Pass %d/%d%s", this.passes_completed + 1, this.number_passes, completionText);
    }

    public int getCurrentPassPercentageCompletion() {
        return (int)(((double)this.wipedBytes / (double)this.totalBytes) * 100);
    }

    public boolean isBlankingPass() {
        return this.passes_completed == this.number_passes;
    }

    public boolean isCompleted() {
        if (this.blank) {
            return this.passes_completed > this.number_passes;
        }
        return this.passes_completed == this.number_passes;
    }

    public boolean failed() {
        return !this.errorMessage.isEmpty();
    }
}