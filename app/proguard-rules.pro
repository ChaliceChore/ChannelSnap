# Entry point launched as root via `app_process` (see RootRestoreHelper). Nothing in
# the app references it directly, so R8 would otherwise strip or rename it.
-keep class dev.chalicechore.channelsnap.root.RestoreMain {
    public static void main(java.lang.String[]);
}

# kotlinx.serialization ships its own consumer rules; the @Serializable models only
# need their generated serializers kept, which those rules already cover.
