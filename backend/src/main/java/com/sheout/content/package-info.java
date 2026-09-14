/**
 * Content module - self-hosted, editable app copy.
 * <p>
 * Deliberately small, and deliberately not a CMS integration: a table of
 * keyed text blocks, a public read, and an operator write from the existing
 * console. No new vendor, no second login, no content living anywhere the
 * rest of SheOut's data does not.
 * <p>
 * Keys are created by migration only (see V17), with a description of where
 * each one appears. Operators edit values. The apps fall back to the text the
 * block was seeded with, so an unreachable server shows yesterday's copy, not
 * a blank screen.
 * <p>
 * Plain text. Line breaks are kept; nothing is rendered as HTML or markdown,
 * so an edit cannot inject markup into either app or the console.
 */
package com.sheout.content;
