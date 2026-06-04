package za.co.capitecbank.fraudrule.util;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class TimeWindowCalculator {

    public LocalDateTime hoursAgo(final int hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    public LocalDateTime minutesAgo(final int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }
}
