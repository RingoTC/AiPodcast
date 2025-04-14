package com.example.aipodcast.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aipodcast.R;
import com.example.aipodcast.model.NewsArticle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Adapter to display news articles in a RecyclerView
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsArticle> articles;
    private final Consumer<NewsArticle> onArticleClickListener;
    private boolean selectMode = false;
    private final Set<Integer> selectedArticles = new HashSet<>();
    private Consumer<Set<NewsArticle>> onSelectionChangedListener;

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

    /**
     * Set whether the adapter is in selection mode
     * @param selectMode true to enable selection mode, false otherwise
     */
    public void setSelectMode(boolean selectMode) {
        this.selectMode = selectMode;
        if (!selectMode) {
            selectedArticles.clear();
            if (onSelectionChangedListener != null) {
                onSelectionChangedListener.accept(getSelectedArticles());
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Get the current selection mode
     * @return true if in selection mode, false otherwise
     */
    public boolean isSelectMode() {
        return selectMode;
    }

    /**
     * Set a listener to be notified when the selection changes
     * @param listener The listener to call
     */
    public void setOnSelectionChangedListener(Consumer<Set<NewsArticle>> listener) {
        this.onSelectionChangedListener = listener;
    }

    /**
     * Get the set of selected articles
     * @return A set of selected news articles
     */
    public Set<NewsArticle> getSelectedArticles() {
        Set<NewsArticle> result = new HashSet<>();
        for (Integer position : selectedArticles) {
            if (position < articles.size()) {
                result.add(articles.get(position));
            }
        }
        return result;
    }

    /**
     * Toggle the selection state of an article
     * @param position The position of the article
     */
    public void toggleSelection(int position) {
        if (selectedArticles.contains(position)) {
            selectedArticles.remove(position);
        } else {
            selectedArticles.add(position);
        }
        notifyItemChanged(position);
        
        if (onSelectionChangedListener != null) {
            onSelectionChangedListener.accept(getSelectedArticles());
        }
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
        holder.bind(article, position, selectMode, selectedArticles.contains(position), 
                onArticleClickListener, p -> toggleSelection(p));
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
        private final CheckBox selectionCheckbox;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.article_title);
            abstractView = itemView.findViewById(R.id.article_abstract);
            sectionView = itemView.findViewById(R.id.article_section);
            dateView = itemView.findViewById(R.id.article_date);
            selectionCheckbox = itemView.findViewById(R.id.article_selection);
        }

        /**
         * Bind article data to views
         * 
         * @param article Article to display
         * @param position Position in the adapter
         * @param selectMode Whether adapter is in selection mode
         * @param isSelected Whether this item is selected
         * @param onArticleClickListener Callback for article click events
         * @param onSelectListener Callback for selection events
         */
        public void bind(NewsArticle article, int position, boolean selectMode, boolean isSelected,
                         Consumer<NewsArticle> onArticleClickListener,
                         Consumer<Integer> onSelectListener) {
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
            
            // Handle selection mode
            if (selectMode) {
                selectionCheckbox.setVisibility(View.VISIBLE);
                selectionCheckbox.setChecked(isSelected);
                selectionCheckbox.setOnClickListener(v -> {
                    if (onSelectListener != null) {
                        onSelectListener.accept(position);
                    }
                });
                
                // Allow click anywhere on the item to toggle selection
                itemView.setOnClickListener(v -> {
                    if (onSelectListener != null) {
                        onSelectListener.accept(position);
                    }
                });
            } else {
                selectionCheckbox.setVisibility(View.GONE);
                
                // Set normal click listener
                itemView.setOnClickListener(v -> {
                    if (onArticleClickListener != null) {
                        onArticleClickListener.accept(article);
                    }
                });
            }
        }
    }
} 