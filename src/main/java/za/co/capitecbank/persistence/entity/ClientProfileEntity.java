package za.co.capitecbank.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.capitecbank.enums.RiskRating;
import za.co.capitecbank.persistence.converter.EncryptedStringConverter;

@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "client_profile")
public class ClientProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String clientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "full_name")
    private String fullName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "id_number")
    private String idNumber;

    private LocalDate accountOpenDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal averageMonthlyIncome;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RiskRating riskRating;

    private LocalDateTime lastProfileUpdateDate;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "client_profile_ppi_change", joinColumns = @JoinColumn(name = "client_profile_id"))
    @OrderBy("changeDate ASC")
    private List<PpiChangeRecord> ppiChangeHistory = new ArrayList<>();

    @Version
    private long version;
}
