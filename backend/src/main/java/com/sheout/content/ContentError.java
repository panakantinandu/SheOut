package com.sheout.content;

/** Expected refusals from ContentApi.updateContent, returned via Result. */
public enum ContentError {

    /** No block has this key. Keys are fixed by migration; an edit cannot create one. */
    NOT_FOUND,

    /** Someone saved this block after the editor loaded it. Their change is not overwritten. */
    STALE_VERSION,

    /** Blank, or longer than the column allows. */
    INVALID_VALUE
}
