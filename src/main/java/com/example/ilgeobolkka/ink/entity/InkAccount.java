package com.example.ilgeobolkka.ink.entity;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.reader.entity.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "ink_account",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_ink_account_reader", columnNames = "reader_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InkAccount {

    private static final int GRANT_AMOUNT = 100;
    private static final int DEDUCTION_AMOUNT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ink_account_reader"))
    private Reader reader;

    @Column(name = "balance", nullable = false)
    private int balance;

    public static InkAccount create(long readerId) {
        InkAccount inkAccount = new InkAccount();
        inkAccount.readerId = readerId;
        inkAccount.balance = 0;
        return inkAccount;
    }

    public void grant() {
        balance = Math.addExact(balance, GRANT_AMOUNT);
    }

    public void deduct() {
        if (balance < DEDUCTION_AMOUNT) {
            throw new InsufficientInkException();
        }
        balance -= DEDUCTION_AMOUNT;
    }
}
