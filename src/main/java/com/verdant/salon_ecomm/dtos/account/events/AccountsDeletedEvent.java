package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record AccountsDeletedEvent(List<User> accounts, User actor) {
}
