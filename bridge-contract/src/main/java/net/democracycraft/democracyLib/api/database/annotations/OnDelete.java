package net.democracycraft.democracyLib.api.database.annotations;

/**
 * Foreign key ON DELETE action.
 */
public enum OnDelete {
    RESTRICT,
    CASCADE,
    SET_NULL,
    NO_ACTION
}

