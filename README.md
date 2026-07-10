# 💈 - Sistema de Agendamento para Barbearia


API RESTful em desenvolvimento com **Java e Spring Boot** para gerenciar agendamentos em uma barbearia.

---
## Principais Funcionalidades

### Cliente
* Cadastro e Login (com JWT)
* Visualizar horários disponíveis
* Agendar, editar e cancelar seus próprios horários
* Editar informações do seu perfil

### Barbeiro / Admin
* Login com acesso admin
* Visualizar e filtrar **todos** os agendamentos
* Gerenciar o status dos agendamentos (concluído, cancelado)
* Gerenciar a disponibilidade da agenda

---
## Tecnologias

* **Java** & **Spring Boot**
* **Spring Security** (Autenticação com JWT)
* **MongoDB** (Banco de Dados NoSQL)
* **Maven**

---

## Arquitetura de Pacotes

A estrutura do projeto é dividida nas seguintes camadas e responsabilidades:

- **`config`**: Configurações do Spring e da segurança geral da aplicação.
- **`controller`**: Controladores REST que expõem os endpoints da API e recebem as requisições HTTP.
- **`dto`**: DTOs (Data Transfer Objects) que definem a estrutura de dados para a comunicação com a API.
- **`entity`**: Entidades que mapeiam os documentos e coleções do banco de dados MongoDB.
- **`exception`**: Handler global (`@ControllerAdvice`) e exceções customizadas para tratamento de erros.
- **`repository`**: Interfaces do Spring Data que definem o acesso e a manipulação de dados no MongoDB.
- **`security`**: Componentes específicos da segurança, como o `TokenService` e o `SecurityFilter` para JWT.
- **`service`**: Camada de serviço onde está a lógica de negócio e as regras da aplicação.

---

## Status do Desenvolvimento

- [x] Estrutura do projeto com Spring Boot
- [x] CRUD de Usuários e sistema de autenticação/autorização com JWT
- [x] Tratamento de exceções global
- [x] Implementação da lógica de Agendamentos
- [x] Notificações de boas vindas ao criar conta e confirmação de Agendamento por email
- [ ] **Em andamaneto:** Frontend
