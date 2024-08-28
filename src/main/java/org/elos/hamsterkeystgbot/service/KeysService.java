package org.elos.hamsterkeystgbot.service;

import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.model.User;
import org.elos.hamsterkeystgbot.repository.KeysRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class KeysService {
    private final KeysRepository keysRepository;
    private final String[] prefixes = {"BIKE", "CUBE", "TRAIN", "MERGE", "TWERK", "POLY", "RACE", "TRIM", "CAFE"};


    public KeysService(KeysRepository keysRepository) {
        this.keysRepository = keysRepository;
    }


    public void deleteAll(List<Keys> keys) {
        keysRepository.deleteAll(keys);
    }

    public List<Keys> findTop4ByPrefixes(String... prefixes) {
        List<Keys> keys = new ArrayList<>();
        for (String prefix : prefixes) {
            List<Keys> keysByPrefix = keysRepository.findTop4ByPrefix(prefix);
            keys.addAll(keysByPrefix);
        }
        return keys;
    }

    public List<Keys> findTop8ByPrefix(String prefix) {
        return keysRepository.findTop8ByPrefix(prefix);
    }

    public long countByPrefix(String prefix) {
        return keysRepository.countByPrefix(prefix);
    }


    public boolean areKeysAvailable() {
        for (String prefix : prefixes) {
            long count = countByPrefix(prefix);
            if (count < 4) {
                return false;
            }
        }
        return true;
    }

    public List<Keys> getKeys() {
        List<Keys> keysAll = new ArrayList<>();
        for (String prefix : prefixes) {
            List<Keys> keys = findTop4ByPrefixes(prefix);
            keysAll.addAll(keys);
            deleteAll(keys);
        }
        return keysAll;
    }

    public String getKeys(User user) {
        StringBuilder keysString = new StringBuilder((Objects.equals(user.getLanguage(), "ru")
                ? "\uD83D\uDD11 Ваши ключи:"
                : "\uD83D\uDD11 Your keys:")
                + "\n\n");
        for (String prefix : prefixes) {
            List<Keys> keys = findTop4ByPrefixes(prefix);
            for (Keys key : keys) {
                keysString.append("<code>").append(prefix).append("-").append(key.getKeyValue()).append("</code>").append("\n");
            }
            keysString.append("\n");
            deleteAll(keys);
        }
        return keysString.toString();
    }
}
