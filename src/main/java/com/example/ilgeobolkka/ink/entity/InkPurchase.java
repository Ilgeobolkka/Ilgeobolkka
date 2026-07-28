package com.example.ilgeobolkka.ink.entity;

import com.example.ilgeobolkka.reader.entity.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ink_purchase",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_ink_purchase_payment", columnNames = "payment_id"),
            @UniqueConstraint(
                    name = "uk_ink_purchase_reader_id",
                    columnNames = {"reader_id", "id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InkPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ink_purchase_reader"))
    private Reader reader;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private InkPurchaseStatus status;

    @Column(name = "amount_won", nullable = false)
    private int amountWon;

    @Column(name = "granted_ink", nullable = false)
    private int grantedInk;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "paid_at", columnDefinition = "DATETIME(6)")
    private Instant paidAt;

    public boolean isPaid() {
        return status == InkPurchaseStatus.PAID;
    }
}
