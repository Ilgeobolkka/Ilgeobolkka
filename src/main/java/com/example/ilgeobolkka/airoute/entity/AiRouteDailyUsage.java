package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.reader.entity.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_route_daily_usage")
@IdClass(AiRouteDailyUsageId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRouteDailyUsage {

    @Id
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_route_daily_usage_reader"))
    private Reader reader;

    @Id
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "generation_count", nullable = false)
    private int generationCount;

    public static AiRouteDailyUsage start(long readerId, LocalDate usageDate) {
        AiRouteDailyUsage usage = new AiRouteDailyUsage();
        usage.readerId = readerId;
        usage.usageDate = usageDate;
        usage.generationCount = 0;
        return usage;
    }

    public void increment() {
        generationCount++;
    }
}
