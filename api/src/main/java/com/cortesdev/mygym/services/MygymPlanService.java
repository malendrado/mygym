package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.MygymPlan;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.PlanRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.PlanResponse;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.repositories.MygymPlanRepository;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogo de planes que mygym le vende a los gimnasios: de aqui salen la landing, el FAQ y la asignacion de plan. */
@Service
@RequiredArgsConstructor
@Transactional
public class MygymPlanService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,40}");

    private final MygymPlanRepository planRepository;
    private final GymSubscriptionRepository subscriptionRepository;

    /** Lo que ve la landing: solo planes activos, ya ordenados. */
    @Transactional(readOnly = true)
    public List<PlanResponse> listPublic() {
        return planRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(MygymPlanService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listAll() {
        return planRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(MygymPlanService::toResponse).toList();
    }

    public PlanResponse create(PlanRequest request) {
        String code = normalizedCode(request.code());
        if (planRepository.existsByCode(code)) {
            throw new SubscriptionConflictException("Ya existe un plan con el código " + code + ".");
        }
        MygymPlan plan = MygymPlan.builder().code(code).build();
        apply(plan, request);
        return toResponse(planRepository.save(plan));
    }

    /** El codigo no se cambia al editar (lo usa la migracion y es la identidad estable del plan). */
    public PlanResponse update(Long id, PlanRequest request) {
        MygymPlan plan = planRepository
                .findById(id)
                .orElseThrow(() -> new InvalidSubscriptionException("Ese plan de mygym no existe."));
        apply(plan, request);
        return toResponse(planRepository.save(plan));
    }

    /** Un plan con gimnasios asignados no se borra (se desactiva): su historial de cobro depende de el. */
    public void delete(Long id) {
        MygymPlan plan = planRepository
                .findById(id)
                .orElseThrow(() -> new InvalidSubscriptionException("Ese plan de mygym no existe."));
        if (subscriptionRepository.existsByPlanId(id)) {
            throw new SubscriptionConflictException("Hay gimnasios con este plan: desactívalo en vez de borrarlo.");
        }
        planRepository.delete(plan);
    }

    private static void apply(MygymPlan plan, PlanRequest request) {
        plan.setName(request.name().trim());
        plan.setClientsLabel(request.clientsLabel().trim());
        plan.setPriceClp(request.priceClp());
        plan.setMaxUsers(request.maxUsers());
        plan.setMaxProfesores(request.maxProfesores());
        plan.setMaxAdmins(request.maxAdmins());
        plan.setFeatures(String.join("\n", request.features().stream().map(String::trim).filter(f -> !f.isEmpty()).toList()));
        plan.setHighlighted(request.highlighted());
        plan.setSortOrder(request.sortOrder());
        plan.setActive(request.active());
    }

    private static String normalizedCode(String raw) {
        String code = raw.trim().toUpperCase().replace(' ', '_');
        if (!CODE.matcher(code).matches()) {
            throw new InvalidSubscriptionException("El código solo puede llevar letras, números y guion bajo.");
        }
        return code;
    }

    static PlanResponse toResponse(MygymPlan plan) {
        List<String> features = plan.getFeatures() == null || plan.getFeatures().isBlank()
                ? List.of()
                : Arrays.stream(plan.getFeatures().split("\n")).map(String::trim).filter(f -> !f.isEmpty()).toList();
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getClientsLabel(),
                plan.getPriceClp(),
                plan.getMaxUsers(),
                plan.getMaxProfesores(),
                plan.getMaxAdmins(),
                features,
                plan.isHighlighted(),
                plan.getSortOrder(),
                plan.isActive());
    }
}
