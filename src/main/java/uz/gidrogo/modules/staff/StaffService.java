package uz.gidrogo.modules.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.staff.StaffDtos.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final uz.gidrogo.modules.cash.CashService cashService;

    @Transactional
    public StaffResponse createStaff(StaffCreateRequest request) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma identifikatori aniqlanmadi");
        }

        if (request.getRole() != Role.MANAGER && request.getRole() != Role.COURIER) {
            throw new BadRequestException("Boss faqat Manager yoki Dastavkachi (COURIER) yarata oladi");
        }

        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new BadRequestException("Ushbu telefon raqamli xodim allaqachon mavjud");
        }

        User staff = User.builder()
                .farmId(farmId)
                .role(request.getRole())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .shiftType(request.getShiftType() != null && !request.getShiftType().isBlank() ? request.getShiftType().trim() : "DAY")
                .status("ACTIVE")
                .build();

        staff = userRepository.save(staff);
        return mapToResponse(staff);
    }

    public List<StaffResponse> getFarmStaff() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma identifikatori aniqlanmadi");
        }

        return userRepository.findAllByFarmId(farmId).stream()
                .filter(u -> u.getRole() == Role.MANAGER || u.getRole() == Role.COURIER)
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public StaffResponse updateStaffStatus(Long staffId, String status) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Xodim topilmadi"));

        if (!farmId.equals(staff.getFarmId())) {
            throw new BadRequestException("Ushbu xodim boshqa fermaga tegishli");
        }

        if (("DELETED".equalsIgnoreCase(status) || "REMOVED".equalsIgnoreCase(status)) && staff.getRole() == Role.COURIER) {
            java.math.BigDecimal balance = cashService.calculateCourierBalance(staff.getId());
            if (balance != null && balance.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "CASH_BALANCE_NOT_ZERO: Kassa balansi 0 dan katta dastavkachini o'chirib bo'lmaydi");
            }
        }

        staff.setStatus(status);
        staff = userRepository.save(staff);
        return mapToResponse(staff);
    }

    @Transactional
    public void deleteStaff(Long staffId) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Xodim topilmadi"));

        if (!farmId.equals(staff.getFarmId())) {
            throw new BadRequestException("Ushbu xodim boshqa fermaga tegishli");
        }

        if (staff.getRole() == Role.COURIER) {
            java.math.BigDecimal balance = cashService.calculateCourierBalance(staff.getId());
            if (balance != null && balance.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "CASH_BALANCE_NOT_ZERO: Kassa balansi 0 dan katta dastavkachini o'chirib bo'lmaydi");
            }
        }

        userRepository.delete(staff);
    }

    private StaffResponse mapToResponse(User user) {
        return StaffResponse.builder()
                .id(user.getId())
                .farmId(user.getFarmId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .shiftType(user.getShiftType())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
