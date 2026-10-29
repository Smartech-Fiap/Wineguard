package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.model.Device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceAccessService {

    private final CurrentUserService currentUser;

    public void requireAccess(Device device) {
        AppUser user = currentUser.require();

        if (user.getRole() == AppUserRole.ADMIN) {
            return;
        }

        AppUser owner = device.getOwner();

        if (owner == null || !user.getId().equals(owner.getId())) {
            throw new ResourceNotFoundException(
                    "Dispositivo não encontrado.");
        }
    }
}
