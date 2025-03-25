package com.example.aipodcast.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aipodcast.R;
import com.example.aipodcast.model.NewsArticle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter to display news articles in a RecyclerView
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsArticle> articles;
    private final Consumer<NewsArticle> onArticleClickListener;

    /**
     * Constructor
     * 
     * @param articles List of news articles to display
     * @param onArticleClickListener Callback for article click events
     */
    public NewsAdapter(List<NewsArticle> articles, Consumer<NewsArticle> onArticleClickListener) {
        this.articles = articles;
        this.onArticleClickListener = onArticleClickListener;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_article, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsArticle article = articles.get(position);
        holder.bind(article, onArticleClickListener);
    }

    @Override
    public int getItemCount() {
        return articles.size();
    }

    /**
     * ViewHolder for news article items
     */
    static class NewsViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView abstractView;
        private final TextView sectionView;
        private final TextView dateView;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.article_title);
            abstractView = itemView.findViewById(R.id.article_abstract);
            sectionView = itemView.findViewById(R.id.article_section);
            dateView = itemView.findViewById(R.id.article_date);
        }

        /**
         * Bind article data to views
         * 
         * @param article Article to display
         * @param onArticleClickListener Callback for article click events
         */
        public void bind(NewsArticle article, Consumer<NewsArticle> onArticleClickListener) {
            titleView.setText(article.getTitle());
            abstractView.setText(article.getAbstract());
            
            String sectionText = article.getSection();
            if (sectionText != null && !sectionText.isEmpty() && !sectionText.equals("Unknown")) {
                sectionView.setText(sectionText);
                sectionView.setVisibility(View.VISIBLE);
            } else {
                sectionView.setVisibility(View.GONE);
            }
            
            String dateText = article.getPublishedDate();
            if (dateText != null && !dateText.isEmpty() && !dateText.equals("Unknown")) {
                // Format date if needed - could extract date formatting to a utility method
                if (dateText.length() > 10) {
                    dateText = dateText.substring(0, 10); // Just get YYYY-MM-DD part
                }
                dateView.setText(dateText);
                dateView.setVisibility(View.VISIBLE);
            } else {
                dateView.setVisibility(View.GONE);
            }
            
            // Set click listener
            itemView.setOnClickListener(v -> {
                if (onArticleClickListener != null) {
                    onArticleClickListener.accept(article);
                }
            });
        }
    }
} 