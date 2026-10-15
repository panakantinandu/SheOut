package com.sheout.driververification.internal;

import com.sheout.driververification.CourtSearchOutcome;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One search a reviewer made on eCourts, by hand: where, under which name,
 * when, what it found. Case details are the minimum - type or section,
 * status, year, court - never the judgment or anyone else's name.
 */
@Entity
@Table(name = "court_screening_searches")
public class CourtScreeningSearchEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID screeningId;
    @Column(nullable = false, length = 100)
    private String district;
    @Column(length = 150)
    private String court;
    @Column(nullable = false, length = 150)
    private String nameVariant;
    @Column(nullable = false)
    private LocalDate searchedOn;
    @Column(nullable = false)
    private UUID reviewerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourtSearchOutcome outcome;
    @Column(length = 1000)
    private String note;
    @Column(length = 100)
    private String caseType;
    @Column(length = 30)
    private String caseStatus;
    private Integer caseYear;
    @Column(length = 512)
    private String screenshotKey;

    protected CourtScreeningSearchEntity() {
    }

    CourtScreeningSearchEntity(UUID screeningId, String district, String court, String nameVariant, LocalDate searchedOn,
                               UUID reviewerId, CourtSearchOutcome outcome, String note, String caseType, String caseStatus,
                               Integer caseYear) {
        this.screeningId = screeningId;
        this.district = district;
        this.court = court;
        this.nameVariant = nameVariant;
        this.searchedOn = searchedOn;
        this.reviewerId = reviewerId;
        this.outcome = outcome;
        this.note = note;
        this.caseType = caseType;
        this.caseStatus = caseStatus;
        this.caseYear = caseYear;
    }

    void attachScreenshot(String key) {
        this.screenshotKey = key;
    }

    /** Retention: the outcome, where and when stay; what identifies anyone goes. */
    void anonymise() {
        this.nameVariant = "-";
        this.note = null;
        this.caseType = null;
        this.caseStatus = null;
        this.caseYear = null;
        this.court = null;
        this.screenshotKey = null;
    }

    public UUID getScreeningId() { return screeningId; }
    public String getDistrict() { return district; }
    public String getCourt() { return court; }
    public String getNameVariant() { return nameVariant; }
    public LocalDate getSearchedOn() { return searchedOn; }
    public UUID getReviewerId() { return reviewerId; }
    public CourtSearchOutcome getOutcome() { return outcome; }
    public String getNote() { return note; }
    public String getCaseType() { return caseType; }
    public String getCaseStatus() { return caseStatus; }
    public Integer getCaseYear() { return caseYear; }
    public String getScreenshotKey() { return screenshotKey; }
}
