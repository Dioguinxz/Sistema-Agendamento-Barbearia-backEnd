package com.example.SistemaBarbearia.controller;

import com.example.SistemaBarbearia.dto.AgendamentoRequestDTO;
import com.example.SistemaBarbearia.dto.AgendamentoResponseDTO;
import com.example.SistemaBarbearia.dto.AgendamentoUpdateDTO;
import com.example.SistemaBarbearia.dto.HorarioOcupadoDTO;
import com.example.SistemaBarbearia.entity.Usuario;
import com.example.SistemaBarbearia.service.AgendamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller responsável por todas as operações relacionadas a Agendamentos.
 * Todas as rotas são protegidas e exigem um usuário autenticado.
 */
@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
public class AgendamentoController {

    private final AgendamentoService agendamentoService;

    /**
     * Endpoint para um cliente criar um novo agendamento.
     * Acesso restrito a usuários com permissão 'CLIENTE'.
     *
     * @param dto           O corpo da requisição com os dados para o novo agendamento.
     * @param clienteLogado O usuário autenticado que está realizando o agendamento.
     * @return ResponseEntity com o agendamento criado e status 201 Created.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<AgendamentoResponseDTO> agendar(@RequestBody @Valid AgendamentoRequestDTO dto, @AuthenticationPrincipal Usuario clienteLogado) {
        AgendamentoResponseDTO agendamentoCriado = agendamentoService.criarAgendamento(dto, clienteLogado);
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamentoCriado);
    }

    /**
     * Endpoint para um usuário listar seus próprios agendamentos.
     * Permite a filtragem opcional por data. Se nenhuma data for fornecida, retorna todos os agendamentos do cliente.
     *
     * @param clienteLogado O usuário autenticado (CLIENTE ou BARBEIRO).
     * @param data          (Opcional) A data para filtrar os agendamentos, no formato AAAA-MM-DD.
     * @return ResponseEntity com a lista de agendamentos do usuário.
     */
    @GetMapping("/meus")
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<List<AgendamentoResponseDTO>> meusAgendamentos(@AuthenticationPrincipal Usuario clienteLogado, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        List<AgendamentoResponseDTO> agendamentos = agendamentoService.listarMeusAgendamentos(clienteLogado.getId(), data);
        return ResponseEntity.ok(agendamentos);
    }

    /**
     * Endpoint para um barbeiro listar os agendamentos de sua agenda.
     * Permite a filtragem opcional por data. Se nenhuma data for fornecida, o serviço assume o dia atual.
     * Acesso restrito a usuários com permissão 'BARBEIRO'.
     *
     * @param data           (Opcional) A data para filtrar os agendamentos, no formato AAAA-MM-DD.
     * @param barbeiroLogado O usuário BARBEIRO autenticado.
     * @return ResponseEntity com a lista de agendamentos da agenda do barbeiro.
     */
    @GetMapping("/todos")
    @PreAuthorize("hasAuthority('BARBEIRO')")
    public ResponseEntity<List<AgendamentoResponseDTO>> todosAgendamentos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data, @AuthenticationPrincipal Usuario barbeiroLogado) {

        List<AgendamentoResponseDTO> agendamentos = agendamentoService.listarTodosAgendamentos(data, barbeiroLogado);
        return ResponseEntity.ok(agendamentos);
    }

    /**
     * Endpoint para buscar um único agendamento pelo seu ID.
     * Acessível por Clientes (apenas para seus próprios agendamentos) e Barbeiros.
     * A lógica de permissão detalhada é tratada na camada de serviço.
     *
     * @param id            O ID do agendamento a ser buscado.
     * @param usuarioLogado O usuário autenticado, para verificação de permissão no serviço.
     * @return ResponseEntity com os dados do agendamento encontrado.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<AgendamentoResponseDTO> buscarAgendamentoPorId(@PathVariable String id, @AuthenticationPrincipal Usuario usuarioLogado) {

        AgendamentoResponseDTO agendamento = agendamentoService.buscarPorId(id, usuarioLogado);
        return ResponseEntity.ok(agendamento);
    }

    /**
     * Endpoint para um cliente atualizar seu próprio agendamento.
     *
     * @param id            O ID do agendamento a ser atualizado.
     * @param dto           O corpo da requisição com os novos dados do agendamento.
     * @param clienteLogado O usuário autenticado, para verificação de permissão no serviço.
     * @return ResponseEntity com os dados do agendamento atualizado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<AgendamentoResponseDTO> atualizarAgendamento(@PathVariable String id, @RequestBody @Valid AgendamentoUpdateDTO dto, @AuthenticationPrincipal Usuario clienteLogado) {

        AgendamentoResponseDTO agendamentoAtualizado = agendamentoService.atualizarAgendamento(id, dto, clienteLogado);
        return ResponseEntity.ok(agendamentoAtualizado);
    }

    /**
     * Endpoint para um CLIENTE cancelar (soft delete) seu próprio agendamento.
     *
     * @param id            O ID do agendamento a ser cancelado.
     * @param clienteLogado O usuário autenticado, para verificação de permissão.
     * @return ResponseEntity com status 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<Void> cancelarAgendamentoPeloCliente(@PathVariable String id, @AuthenticationPrincipal Usuario clienteLogado) {

        agendamentoService.cancelarPeloCliente(id, clienteLogado);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint para um BARBEIRO cancelar um agendamento de sua própria agenda.
     *
     * @param id             O ID do agendamento a ser cancelado.
     * @param barbeiroLogado O usuário BARBEIRO autenticado, para verificação de permissão.
     * @return ResponseEntity com status 204 No Content.
     */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('BARBEIRO')")
    public ResponseEntity<Void> cancelarAgendamentoPeloBarbeiro(@PathVariable String id, @AuthenticationPrincipal Usuario barbeiroLogado) {

        agendamentoService.cancelarPeloBarbeiro(id, barbeiroLogado);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint para um BARBEIRO marcar um agendamento como CONCLUÍDO.
     *
     * @param id             O ID do agendamento a ser concluído.
     * @param barbeiroLogado O usuário BARBEIRO autenticado, para verificação de permissão.
     * @return ResponseEntity com status 204 No Content.
     */
    @PatchMapping("/{id}/concluir")
    @PreAuthorize("hasAuthority('BARBEIRO')")
    public ResponseEntity<Void> concluirAgendamento(@PathVariable String id, @AuthenticationPrincipal Usuario barbeiroLogado) {

        agendamentoService.marcarComoConcluido(id, barbeiroLogado);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/horarios-ocupados")
    @PreAuthorize("hasAuthority('CLIENTE')")
    public ResponseEntity<List<HorarioOcupadoDTO>> horariosOcupados(
            @RequestParam String barbeiroId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(agendamentoService.listarHorariosOcupados(barbeiroId, data));
    }
}