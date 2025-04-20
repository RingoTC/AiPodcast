package com.example.aipodcast.model;
import java.io.Serializable;
public class NewsArticle implements Serializable {
    private static final long serialVersionUID = 1L;
    private String title;
    private String abstract_;
    private String url;
    private String section;
    private String publishedDate;
    private String fullBodyText;
    public NewsArticle(String title, String abstract_, String url, String section, String publishedDate) {
        this.title = title;
        this.abstract_ = abstract_;
        this.url = url;
        this.section = section;
        this.publishedDate = publishedDate;
        this.fullBodyText = "";

    }
    // New constructor with fullBodyText
    public NewsArticle(String title, String abstract_, String url, String section, String publishedDate, String fullBodyText) {
        this.title = title;
        this.abstract_ = abstract_;
        this.url = url;
        this.section = section;
        this.publishedDate = publishedDate;
        this.fullBodyText = fullBodyText;
    }
    public String getTitle() { return title; }
    public String getAbstract() { return abstract_; }
    public String getUrl() { return url; }
    public String getSection() { return section; }
    public String getPublishedDate() { return publishedDate; }
    public void setTitle(String title) { this.title = title; }
    public void setAbstract(String abstract_) { this.abstract_ = abstract_; }
    public void setUrl(String url) { this.url = url; }
    public void setSection(String section) { this.section = section; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }
    public String getFullBodyText() {
        return fullBodyText;
    }

    public void setFullBodyText(String fullBodyText) {
        this.fullBodyText = fullBodyText;
    }

    // Helper method to get the best available content
    public String getFullContent() {
        if (fullBodyText != null && !fullBodyText.isEmpty()) {
            return fullBodyText;
        }
        return abstract_;
    }

}