package org.elos.hamsterkeystgbot.service;

import org.elos.hamsterkeystgbot.model.User;
import org.elos.hamsterkeystgbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findByUserId(Long id) {
        return userRepository.findByUserId(id).orElse(null);
    }
    public boolean existsByUserId(Long id) {
        return userRepository.existsByUserId(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public String getRemainingTimeToNextKeys(User user) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime lastRequestZone = user.getLastRequest().atZone(zone);
        ZonedDateTime nextAvailableRequest = lastRequestZone.toLocalDate().plusDays(1).atStartOfDay(zone);

        long hoursUntilNextRequest = ChronoUnit.HOURS.between(now, nextAvailableRequest);
        long minutesUntilNextRequest = ChronoUnit.MINUTES.between(now, nextAvailableRequest) % 60;

        String remainingTime;
        String strhours = "";
        String strmins = "";
        if (user.getLanguage().equals("ru")) {
            strhours = "часов";
            strmins = "минут";
        } else if (user.getLanguage().equals("en")) {
            strhours = "hours";
            strmins = "minutes";
        }
        if (hoursUntilNextRequest > 0) {
            remainingTime = String.format("%d %s %d %s", hoursUntilNextRequest, strhours, minutesUntilNextRequest, strmins);
        } else {
            remainingTime = String.format("%d %s", minutesUntilNextRequest, strmins);
        }
        return remainingTime;
    }


    public User save(User user) {
        return userRepository.save(user);
    }
}
