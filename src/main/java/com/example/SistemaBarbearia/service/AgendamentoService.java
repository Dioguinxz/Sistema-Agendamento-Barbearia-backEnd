package com.example.SistemaBarbearia.service;

import com.example.SistemaBarbearia.dto.AgendamentoRequestDTO;
import com.example.SistemaBarbearia.dto.AgendamentoResponseDTO;
import com.example.SistemaBarbearia.dto.AgendamentoUpdateDTO;
import com.example.SistemaBarbearia.dto.HorarioOcupadoDTO;
import com.example.SistemaBarbearia.entity.*;
import com.example.SistemaBarbearia.exceptions.AgendamentoException;
import com.example.SistemaBarbearia.exceptions.CancelamentoForaDoPrazoException;
import com.example.SistemaBarbearia.repository.AgendamentoRepository;
import com.example.SistemaBarbearia.repository.UsuarioRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    public AgendamentoResponseDTO criarAgendamento(AgendamentoRequestDTO dto, Usuario cliente) {
        LocalDateTime horarioAgendamento = dto.horario();


        // Validação de horário no passado
        if (horarioAgendamento.isBefore(LocalDateTime.now())) {
            throw new AgendamentoException("Não é possível agendar um horário no passado.");
        }

        // Validação do dia da semana (não funciona aos domingos)
        if (horarioAgendamento.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AgendamentoException("Fora do horário de funcionamento. A barbearia não funciona aos domingos.");
        }

        // Validação da hora do agendamento (das 9h às 18h)
        LocalTime horaAgendamento = horarioAgendamento.toLocalTime();
        LocalTime horarioAbertura = LocalTime.of(9, 0);
        LocalTime horarioFechamento = LocalTime.of(18, 0);

        if (horaAgendamento.isBefore(horarioAbertura) || horaAgendamento.isAfter(horarioFechamento)) {
            throw new AgendamentoException("Fora do horário de funcionamento. A barbearia funciona das 9h às 18h.");
        }


        // Validação de intervalo de agendamento (00 ou 30 minutos)
        if (horarioAgendamento.getMinute() != 0 && horarioAgendamento.getMinute() != 30) {
            throw new AgendamentoException("Horário inválido. Agendamentos são permitidos apenas em intervalos de 30 minutos (ex: 15:00, 15:30).");
        }

        // Validação do barbeiro
        Usuario barbeiro = usuarioRepository.findById(dto.barbeiroId())
                .orElseThrow(() -> new AgendamentoException("Barbeiro não encontrado"));
        if (barbeiro.getTipo() != TipoUsuario.BARBEIRO) {
            throw new AgendamentoException("O ID fornecido não pertence a um barbeiro.");
        }

        // Lógica de conflito de horários
        int duracaoServico = getDuracaoPorTipo(dto.tipoServico());
        LocalDateTime horarioInicioNovo = dto.horario();
        LocalDateTime horarioFimNovo = horarioInicioNovo.plusMinutes(duracaoServico);

        List<Agendamento> agendamentosProximos = agendamentoRepository.findByBarbeiroIdAndHorarioBetween(
                dto.barbeiroId(),
                horarioInicioNovo.minusHours(2),
                horarioInicioNovo.plusHours(2)
        );

        for (Agendamento agendamentoExistente : agendamentosProximos) {
            LocalDateTime horarioInicioExistente = agendamentoExistente.getHorario();
            int duracaoExistente = getDuracaoPorTipo(agendamentoExistente.getTipoServico());
            LocalDateTime horarioFimExistente = horarioInicioExistente.plusMinutes(duracaoExistente);

            if (horarioInicioNovo.isBefore(horarioFimExistente) && horarioFimNovo.isAfter(horarioInicioExistente)) {
                throw new AgendamentoException("Horário já ocupado.");
            }
        }

        // Criação do agendamento
        Agendamento novoAgendamento = new Agendamento();
        novoAgendamento.setUsuarioId(cliente.getId());
        novoAgendamento.setBarbeiroId(dto.barbeiroId());
        novoAgendamento.setNomeCliente(cliente.getNome());
        novoAgendamento.setNomeBarbeiro(barbeiro.getNome());
        novoAgendamento.setTipoServico(dto.tipoServico());
        novoAgendamento.setHorario(dto.horario());
        novoAgendamento.setStatus(StatusAgendamento.AGENDADO);

        Agendamento agendamentoSalvo = agendamentoRepository.save(novoAgendamento);
        emailService.enviarEmailConfirmacaoAgendamento(agendamentoSalvo, cliente.getEmail(), barbeiro.getEmail());

        return new AgendamentoResponseDTO(agendamentoSalvo);
    }

    // Método auxiliar para definir a duração de cada serviço em minutos
    private int getDuracaoPorTipo(TipoServico tipoServico) {
        return switch (tipoServico) {
            case CORTE, BARBA -> 30;
            case CORTE_E_BARBA -> 60;
        };
    }

    /**
     * Lista os agendamentos de um cliente específico.
     * Se uma data for fornecida, filtra os agendamentos para aquele dia.
     *
     * @param clienteId O ID do cliente.
     * @param data      (Opcional) A data para filtrar.
     * @return Uma lista de agendamentos do cliente.
     */
    public List<AgendamentoResponseDTO> listarMeusAgendamentos(String clienteId, LocalDate data) {
        // Se nenhuma data for passada, retorna todos os agendamentos do cliente.
        if (data == null) {
            return agendamentoRepository.findByUsuarioId(clienteId).stream()
                    .map(AgendamentoResponseDTO::new)
                    .collect(Collectors.toList());
        }

        // Se uma data foi passada, calcula o início e o fim do dia e filtra.
        LocalDateTime inicioDoDia = data.atStartOfDay();
        LocalDateTime fimDoDia = data.atTime(LocalTime.MAX);

        return agendamentoRepository.findByUsuarioIdAndHorarioBetween(clienteId, inicioDoDia, fimDoDia).stream()
                .map(AgendamentoResponseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Lista os agendamentos da agenda de um barbeiro específico.
     * Se uma data for fornecida, filtra para aquele dia. Se não, usa o dia de hoje.
     *
     * @param data           (Opcional) A data para filtrar.
     * @param barbeiroLogado O usuário BARBEIRO autenticado.
     * @return Uma lista de agendamentos do barbeiro.
     */
    public List<AgendamentoResponseDTO> listarTodosAgendamentos(LocalDate data, Usuario barbeiroLogado) {
        // Define a data do filtro: ou a data fornecida, ou o dia de hoje.
        LocalDate dataFiltro = (data != null) ? data : LocalDate.now();

        LocalDateTime inicioDoDia = dataFiltro.atStartOfDay();
        LocalDateTime fimDoDia = dataFiltro.atTime(LocalTime.MAX);

        return agendamentoRepository.findByBarbeiroIdAndHorarioBetween(barbeiroLogado.getId(), inicioDoDia, fimDoDia)
                .stream()
                .map(AgendamentoResponseDTO::new)
                .collect(Collectors.toList());
    }

    public AgendamentoResponseDTO buscarPorId(String agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new AgendamentoException("Agendamento não encontrado com o ID: " + agendamentoId));

        // permissões
        boolean isClienteDono = agendamento.getUsuarioId().equals(usuarioLogado.getId());
        boolean isBarbeiroDoAgendamento = agendamento.getBarbeiroId().equals(usuarioLogado.getId());
        boolean isAdminBarbeiro = usuarioLogado.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("BARBEIRO"));

        if (!isClienteDono && !isBarbeiroDoAgendamento && !isAdminBarbeiro) {
            throw new AgendamentoException("Acesso negado. Você não tem permissão para visualizar este agendamento.");
        }

        return new AgendamentoResponseDTO(agendamento);
    }


    public AgendamentoResponseDTO atualizarAgendamento(String agendamentoId, AgendamentoUpdateDTO dto, Usuario clienteLogado) {
        Agendamento agendamentoExistente = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new AgendamentoException("Agendamento não encontrado com o ID: " + agendamentoId));

        if (!agendamentoExistente.getUsuarioId().equals(clienteLogado.getId())) {
            throw new AgendamentoException("Você não tem permissão para editar este agendamento.");
        }

        LocalDateTime novoHorario = dto.horario();

        if (novoHorario.isBefore(LocalDateTime.now())) {
            throw new AgendamentoException("Não é possível agendar um horário no passado.");
        }

        if (novoHorario.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AgendamentoException("Desculpe, a barbearia não funciona aos domingos.");
        }

        LocalTime horaAgendamento = novoHorario.toLocalTime();
        LocalTime horarioAbertura = LocalTime.of(9, 0);
        LocalTime horarioFechamento = LocalTime.of(18, 0);
        if (horaAgendamento.isBefore(horarioAbertura) || horaAgendamento.isAfter(horarioFechamento)) {
            throw new AgendamentoException("Fora do horário de funcionamento. A barbearia funciona das 9h às 18h.");
        }

        if (novoHorario.getMinute() != 0 && novoHorario.getMinute() != 30) {
            throw new AgendamentoException("Horário inválido. Agendamentos são permitidos apenas em intervalos de 30 minutos.");
        }

        Usuario barbeiro = usuarioRepository.findById(dto.barbeiroId())
                .orElseThrow(() -> new AgendamentoException("Barbeiro não encontrado."));
        if (barbeiro.getTipo() != TipoUsuario.BARBEIRO) {
            throw new AgendamentoException("O ID fornecido não pertence a um barbeiro.");
        }

        int duracaoServico = getDuracaoPorTipo(dto.tipoServico());
        LocalDateTime horarioFimNovo = novoHorario.plusMinutes(duracaoServico);

        List<Agendamento> agendamentosProximos = agendamentoRepository.findByBarbeiroIdAndHorarioBetween(
                dto.barbeiroId(),
                novoHorario.minusHours(2),
                novoHorario.plusHours(2)
        );

        for (Agendamento outroAgendamento : agendamentosProximos) {
            if (outroAgendamento.getId().equals(agendamentoId)) {
                continue;
            }

            LocalDateTime horarioInicioExistente = outroAgendamento.getHorario();
            int duracaoExistente = getDuracaoPorTipo(outroAgendamento.getTipoServico());
            LocalDateTime horarioFimExistente = horarioInicioExistente.plusMinutes(duracaoExistente);

            if (novoHorario.isBefore(horarioFimExistente) && horarioFimNovo.isAfter(horarioInicioExistente)) {
                throw new AgendamentoException("Conflito de horário. O período solicitado já está ocupado por outro agendamento.");
            }
        }

        agendamentoExistente.setBarbeiroId(dto.barbeiroId());
        agendamentoExistente.setNomeBarbeiro(barbeiro.getNome());
        agendamentoExistente.setTipoServico(dto.tipoServico());
        agendamentoExistente.setHorario(dto.horario());

        Agendamento agendamentoAtualizado = agendamentoRepository.save(agendamentoExistente);

        return new AgendamentoResponseDTO(agendamentoAtualizado);
    }

    /**
     * Lógica para um CLIENTE cancelar seu próprio agendamento.
     * Contém regras de negócio de prazo.
     */
    public void cancelarPeloCliente(String agendamentoId, Usuario clienteLogado) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new AgendamentoException("Agendamento não encontrado."));

        // Validação se o agendamento pertence ao usuário logado
        if (!agendamento.getUsuarioId().equals(clienteLogado.getId())) {
            throw new AgendamentoException("Acesso negado. Você só pode cancelar seus próprios agendamentos.");
        }

        // Validação se agendamento já foi concluído ou cancelado
        if (agendamento.getStatus() != StatusAgendamento.AGENDADO) {
            throw new AgendamentoException("Este agendamento não pode mais ser cancelado, pois seu status é '" + agendamento.getStatus() + "'.");
        }

        // Validação de antecedência de cancelamento
        var agora = LocalDateTime.now();
        var horarioAgendamento = agendamento.getHorario();
        var horasDeAntecedencia = Duration.between(agora, horarioAgendamento).toHours();

        if (horasDeAntecedencia < 1) {
            throw new CancelamentoForaDoPrazoException("O cancelamento só é permitido com no mínimo 1 hora de antecedência.");
        }

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        agendamentoRepository.save(agendamento);
    }

    /**
     * Lógica para um BARBEIRO cancelar um agendamento da sua agenda.
     * Não contém regras de prazo.
     */
    public void cancelarPeloBarbeiro(String agendamentoId, Usuario barbeiroLogado) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new AgendamentoException("Agendamento não encontrado."));

        // Validação se o agendamento pertence à agenda do barbeiro logado
        if (!agendamento.getBarbeiroId().equals(barbeiroLogado.getId())) {
            throw new AgendamentoException("Acesso negado. Este agendamento não pertence à sua agenda.");
        }

        // Validação se agendamento já foi concluído ou cancelado
        if (agendamento.getStatus() != StatusAgendamento.AGENDADO) {
            throw new AgendamentoException("Este agendamento não pode mais ser cancelado.");
        }

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        agendamentoRepository.save(agendamento);
    }
    /**
     * Lógica para um BARBEIRO marcar um agendamento da sua agenda como CONCLUÍDO.
     *
     * @param agendamentoId   O ID do agendamento a ser concluído.
     * @param barbeiroLogado  O usuário BARBEIRO autenticado.
     */
    public void marcarComoConcluido(String agendamentoId, Usuario barbeiroLogado) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new AgendamentoException("Agendamento não encontrado com o ID: " + agendamentoId));

        // barbeiro só pode concluir agendamentos da sua própria agenda
        if (!agendamento.getBarbeiroId().equals(barbeiroLogado.getId())) {
            throw new AgendamentoException("Acesso negado. Este agendamento não pertence à sua agenda.");
        }

        // agendamento só pode ser concluído se seu status for AGENDADO
        if (agendamento.getStatus() != StatusAgendamento.AGENDADO) {
            throw new AgendamentoException("Apenas agendamentos com status 'AGENDADO' podem ser concluídos.");
        }

        // não permite concluir um agendamento que ainda não aconteceu
        if (agendamento.getHorario().isAfter(LocalDateTime.now())) {
            throw new AgendamentoException("Este agendamento ainda não pode ser marcado como concluído, pois seu horário é no futuro.");
        }

        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamentoRepository.save(agendamento);
    }

    
    public List<HorarioOcupadoDTO> listarHorariosOcupados(String barbeiroId, LocalDate data) {
        LocalDateTime inicioDoDia = data.atStartOfDay();
        LocalDateTime fimDoDia = data.atTime(LocalTime.MAX);
        return agendamentoRepository.findByBarbeiroIdAndHorarioBetween(barbeiroId, inicioDoDia, fimDoDia).stream()
                .filter(a -> a.getStatus() == StatusAgendamento.AGENDADO)
                .map(a -> new HorarioOcupadoDTO(a.getHorario(), a.getTipoServico()))
                .collect(Collectors.toList());
    }

}
