package com.example.aipodcast.model;
import java.io.Serializable;
public class NewsArticle implements Serializable {
    private static final long serialVersionUID = 1L;
    private String title;
    private String abstract_;
    private String url;
    private String section;
    private String publishedDate;
    public NewsArticle(String title, String abstract_, String url, String section, String publishedDate) {
        this.title = title;
        this.abstract_ = abstract_;
        this.url = url;
        this.section = section;
        this.publishedDate = publishedDate;
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
}