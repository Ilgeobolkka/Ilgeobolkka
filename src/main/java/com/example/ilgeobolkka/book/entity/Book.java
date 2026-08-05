package com.example.ilgeobolkka.book.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "book",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_book_id_price",
                        columnNames = {"id", "price_won"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "author", nullable = false, length = 255)
    private String author;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cover_image_path", length = 500)
    private String coverImagePath;

    @Column(name = "total_page_count", nullable = false)
    private int totalPageCount;

    @Column(name = "price_won", nullable = false)
    private int priceWon;
}
