package org.elos.hamsterkeystgbot.controller;

import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.service.KeysService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("keys")
public class ApiController {

    private final KeysService keysService;

    @Autowired
    public ApiController(KeysService keysService) {
        this.keysService = keysService;
    }

    @GetMapping
    public List<Keys> getKeys() {
        return keysService.getKeys();
    }


}
