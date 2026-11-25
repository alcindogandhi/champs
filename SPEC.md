# Champs — Especificação

Documento de referência do comportamento e das decisões técnicas do app. Para compilar, testar e contribuir, veja o [README](README.md).

## 1. Visão geral

App Android de estatísticas de futebol, com foco nos campeonatos brasileiros, que recria as funcionalidades do Kamps (descontinuado em 2019).

- **Plataforma:** Android 10+ (`minSdk 29`), Kotlin, Jetpack Compose com Material 3.
- **Dados:** API pública e não oficial da ESPN e, para os campeonatos que ela não cobre, o ge (globoesporte). Sem chave e sem backend próprio (§4).
- **Sem anúncios, sem conta, sem rastreamento.** Nenhuma biblioteca proprietária (Google Play Services, Firebase), então o app é compatível com o F-Droid. Permissões: internet e, para as notificações (F11), notificações, serviço em primeiro plano, alarmes exatos (opcional) e reinício do aparelho.
- **Idiomas:** português do Brasil (padrão) e inglês.

## 2. Funcionalidades

| # | Funcionalidade | Comportamento |
|---|---|---|
| F1 | Campeonatos seguidos | O usuário escolhe quais campeonatos aparecem no menu lateral e em "Jogos de hoje". No primeiro uso: Série A, Série B, Copa do Brasil e Libertadores. O menu segue a ordem do catálogo. |
| F2 | Tabela | Colunas P, J, V, E, D, GP, GC, SG e %, das quais o usuário escolhe quais mostrar (P sempre aparece). Faixa colorida por zona, com legenda. Com grupos, uma tabela por grupo. Tocar num time abre a tela do time (F7). |
| F3 | Jogos | Pontos corridos e fases de grupos: seletor de rodada `<` `>`, que abre na rodada atual (§5.3). Mata-mata: confrontos com ida e volta, agregado, pênaltis e classificado. Com mais de uma fase, há chips para trocar de fase. |
| F4 | Artilheiros | Posição (empatados dividem a posição), jogador, time, jogos e gols. |
| F5 | Jogos de hoje | Jogos do dia nos campeonatos seguidos, agrupados por campeonato, com troca de dia e filtro "só ao vivo". Na tela de hoje, atualiza sozinho a cada 30 s se há jogo ao vivo, ou a cada 2 min, enquanto visível. |
| F6 | Detalhe do jogo | Placar, status, data e estádio. Abas: **Lances** (narração do mais recente para o mais antigo, com destaque e ícone para gol, cartão, substituição e VAR), **Escalação** (campinho ou lista, com reservas e quem entrou e saiu) e **Comparar** (posse, finalizações, escanteios, defesas, passes, faltas, impedimentos e cartões). Atualiza a cada 20 s durante o jogo. |
| F7 | Time e time do coração | Posição e campanha no campeonato, próximos jogos e resultados. O coração marca o time do coração, que aparece no topo do menu e em destaque nas tabelas. Pode também ser escolhido numa lista, com busca, dos times dos campeonatos seguidos. |
| F8 | Simulador | Só para campeonatos de pontos corridos. O usuário cria simulações com nome, dá placares aos jogos ainda não encerrados (botões − e + para cada time) e vê a tabela recalculada, com a variação de posição em relação à tabela real. Jogos encerrados usam o placar real. Ações: limpar um placar, limpar todos, renomear, apagar e compartilhar a tabela como texto. |
| F9 | Configurações | Tema (sistema, dia, noite), time do coração, campeonatos, fonte dos dados, apagar todos os dados (cache, simulações e preferências, com confirmação), versão, código-fonte, licença e política de privacidade. |
| F10 | Offline | Toda resposta da ESPN e do ge fica em cache. As telas abrem com o último dado conhecido e atualizam em seguida. Sem rede, um aviso mostra de quando é o dado. "Puxar para atualizar" em todas as listas. |
| F11 | Notificações | Avisos de **início de jogo** e de **cada gol** (time, placar parcial, minuto e autor, com "contra" ou "pênalti" quando for o caso). Valem para os jogos marcados com o sino na tela do jogo e, se a opção estiver ligada, para os jogos do time do coração nos próximos 14 dias, incluídos sozinhos. Tocar no aviso abre o jogo. Os avisos de um jogo ficam agrupados. A permissão de notificações (Android 13+) é pedida ao acompanhar o primeiro jogo ou ao escolher o time do coração. Ver §5.7. |

**Fora do escopo:** avisos de intervalo, fim de jogo e cartões, widgets, notícias e simulação de mata-mata.

## 3. Catálogo de campeonatos

Definido em `catalog/CompetitionCatalog.kt`. Cada campeonato tem um id (o slug da ESPN, ou `ge.<nome>` para os do ge com o caminho da página no ge), o nome, a região, se pode ser simulado e as zonas da tabela.

- **Brasil:** Série A e Série B (simuláveis), Copa do Brasil, Copa do Nordeste, Paulista, Carioca, Mineiro e Gaúcho.
- **América do Sul:** Libertadores, Sul-Americana e Campeonato Argentino.
- **Europa:** Champions League, Europa League e Conference League. Premier League, La Liga, Serie A, Bundesliga, Ligue 1, Liga Portugal e Eredivisie (simuláveis).
- **Mundo:** Copa do Mundo e MLS.

O que a ESPN não cobre vem do ge (§4.1): Séries C e D, Brasileirão Feminino, Copa Verde, Copa São Paulo de Futebol Júnior, os estaduais dos outros 23 estados e do DF, divisões de acesso de SP, SC, GO, MG, PB e ES, Copa Paulista, Copa Rio, Copa Espírito Santo, Sul-Americano Sub-17 e Mundial Sub-17. A região **Estaduais** reúne todos os estaduais, venham da ESPN ou do ge. Ficaram de fora as páginas do ge com edição antiga (ex.: Carioca B1 2019), conferido com `scripts/check_ge.py`.

**Zonas:** quando a ESPN informa a faixa de um time (`note` com cor e descrição, como nos grupos da Libertadores), o app usa essa informação. Senão, usa as zonas aproximadas do catálogo (G4/G6, Sul-Americana, acesso, rebaixamento), e a legenda avisa que o número de vagas pode mudar a cada temporada.

## 4. Fontes de dados

### 4.0 ESPN

Base `https://site.api.espn.com`. Todo pedido leva o idioma do app: `lang=pt&region=br` em português, `lang=en&region=us` nos demais.

| Uso | Endpoint |
|---|---|
| Jogos de um dia ou mês | `/apis/site/v2/sports/soccer/{slug}/scoreboard?dates=AAAAMMDD` ou `dates=AAAAMM` |
| Datas da temporada atual | `/apis/site/v2/sports/soccer/{slug}/scoreboard` (sem data): `leagues[0].season.startDate/endDate` |
| Classificação | `/apis/v2/sports/soccer/{slug}/standings` |
| Detalhe do jogo | `/apis/site/v2/sports/soccer/{slug}/summary?event={id}` |
| Artilheiros | `/apis/site/v2/sports/soccer/{slug}/statistics` (categoria `goalsLeaders`) |
| Times | `/apis/site/v2/sports/soccer/{slug}/teams` |
| Agenda do time | `/apis/site/v2/sports/soccer/{slug}/teams/{id}/schedule`, com `fixture=true` para os próximos jogos |

Limitações da API: intervalos de datas (`dates=A-B`) dão erro 400, e `dates=AAAA` devolve no máximo 100 jogos. Por isso a temporada é buscada mês a mês, entre o início e o fim informados pela ESPN, o que também cobre temporadas europeias que cruzam o ano.

A ESPN agrupa os jogos por dia no horário de Nova York. Para "Jogos de hoje", o app busca os dois dias da ESPN que cobrem o dia no fuso do aparelho e filtra pelo horário local.

**Validade do cache** (`data/FootballRepository.kt`):

| Dado | Validade |
|---|---|
| Jogos do dia de hoje | 30 s |
| Jogos de outro dia | 10 min |
| Mês corrente da temporada | 1 min |
| Meses passados | 7 dias |
| Meses futuros | 1 h |
| Datas da temporada | 6 h |
| Classificação | 5 min |
| Artilheiros | 1 h |
| Detalhe de jogo ao vivo / agendado / encerrado | 20 s / 1 min / 1 dia |
| Times | 1 dia |
| Agenda do time | 10 min |

O cache fica em `files/espn/` (um arquivo por URL) e fica fora do backup do Android.

### 4.1 ge (globoesporte)

Usado só para os campeonatos que a ESPN não cobre (`data/GeDataSource.kt`, `data/remote/ge/`). O `RoutingDataSource` escolhe a fonte de cada campeonato. O ge tem dois formatos:

| Formato | Como funciona |
|---|---|
| **v1** (maioria) | A página do campeonato (`ge.globo.com/<caminho>/`, lida a cada 12 h) informa o id da tabela (`tUUID`) e as fases. Os dados vêm da API JSON pública, a mesma do Kamps: `api.globoesporte.globo.com/tabela/{id}/fase/{fase}/classificacao/` (tabela, grupos ou chaves do mata-mata, conforme o tipo da fase) e `.../rodada/{n}/jogos/` (jogos de todos os grupos). Respostas de 1 a 4 KB. |
| **v2** (Baiano, Cearense, Pernambucano, Piauiense, Brasiliense, Capixaba, Goiano 2ª, Copa Rio, Mundial Sub-17) | A página já traz os dados embutidos (`getStandingData` e `getMatchListData`), sem API pública. Cada atualização baixa a página (~120 KB), então nunca mais que a cada 2 min. A página traz a tabela de todas as fases, as chaves completas do mata-mata e só a **rodada atual** das fases de liga. |

Diferenças em relação à ESPN:
- As rodadas e os nomes das fases são os oficiais do ge (sem dedução), e as faixas da tabela vêm com nome e cor.
- Horários do ge estão em Brasília. O v1 não tem status explícito do jogo: não começado → agendado; transmissão "ENCERRADA" ou mais de 2h30 desde o início → encerrado; senão, em andamento. No v2, `moment` (`PAST`/`FUTURE`) e `result` dão o status.
- Ids de jogos e times ganham o prefixo `ge-`, para não colidir com os da ESPN.
- Escudos em SVG (decodificados com `coil-svg`).
- **Não há** artilharia, lances, escalações nem estatísticas por API. A aba Artilheiros avisa isso, e o detalhe do jogo mostra placar, data e estádio, com um botão para a página do jogo no ge.
- A tabela mostra as fases de liga e de grupos, a mais recente primeiro, com o nome da fase quando há mais de uma.
- Notificações funcionam: a consulta ao vivo pede só a fase atual e a rodada atual (v1, ~1–2 KB) ou a página (v2, no máximo a cada 2 min). Como o ge não traz os autores dos gols, o aviso de gol sai sem autor.

## 5. Regras de cálculo

### 5.1 Fases

Os jogos são agrupados pelo `season.slug` da ESPN (ex.: `quarterfinals`, `group-stage`, `2026-brasileiro-serie-a`). Uma fase é **mata-mata** se o slug tiver `final`, `round-of`, `knockout` ou `playoff`. É **de liga** se o slug começar com o ano ou tiver `group`, `league`, `regular`, `season`, `taca`, `relegation` ou `liguilla`. Nos demais casos, é mata-mata se cada time enfrentar um único adversário na fase. Os nomes das fases são traduzidos em `ui/common/PhaseNames.kt`.

### 5.2 Rodadas

A ESPN não informa o número da rodada. Porém, os ids dos jogos são criados na ordem da tabela oficial (`domain/SeasonBuilder.kt`):

1. Um jogo adiado (`STATUS_POSTPONED`/`CANCELED`) que ganhou um jogo de reposição (mesmos mandante e visitante, id maior) sai da lista, e a reposição herda a posição dele.
2. Dentro da fase, os jogos são ordenados pelo id e fatiados em rodadas de `times / 2` jogos. Uma rodada também fecha antes se um time fosse jogar duas vezes nela.

Validado com o Brasileirão 2026 (38 rodadas de 10 jogos e 20 times distintos em cada, incluindo três jogos adiados), a Premier League 2025-26, a fase de grupos da Libertadores 2026 e a fase de liga do Paulista 2026.

### 5.3 Rodada atual

É a primeira rodada em que mais da metade dos jogos ainda não aconteceu (agendados ou ao vivo). No mata-mata, é a primeira fase com jogo pendente. Se tudo já foi jogado, é a última.

### 5.4 Confrontos de mata-mata

Os jogos da fase são agrupados pelo par de times. O agregado soma os gols dos jogos encerrados ou em andamento. O classificado é o que a ESPN indica na série. Sem essa indicação e com todos os jogos encerrados, é quem tem mais gols no agregado; empatado o agregado, é o vencedor do último jogo, decidido nos pênaltis.

### 5.5 Classificação do simulador

`domain/StandingsCalculator.kt`: vitória vale 3 pontos e empate vale 1. Os desempates seguem o Brasileirão: pontos, vitórias, saldo de gols, gols pró e, por fim, ordem alfabética como critério estável. Confronto direto, cartões e sorteio não entram. Um teste confere que a tabela recalculada a partir dos jogos encerrados bate com a tabela oficial da ESPN.

### 5.6 Escalação no campinho

`domain/LineupLayout.kt`: a ESPN informa o esquema tático ("3-4-2-1") e a sigla da posição de cada titular (G, ZC, ZE, LD, VOL, MC, ME, A...). Os titulares são ordenados pela profundidade da posição (goleiro, defesa, volante, meio, pontas, centroavante) e pelo `formationPlace`, e depois fatiados conforme o esquema. Dentro da linha, a ordem é da esquerda para a direita pelo sufixo E/C/D. Sem esquema válido ou sem 11 titulares, só a lista é mostrada.

### 5.7 Notificações sem servidor

O Champs não tem servidor (o Kamps usava Firebase), então é o próprio aparelho que consulta a ESPN (`notifications/`):

- **O que consultar:** os jogos acompanhados ficam na tabela `followed_match` com o último estado visto (status e placar). Um jogo entra na janela de consulta 2 min antes do início e sai 4 h depois (6 h se ainda estiver em andamento). Cada consulta é **um pedido por campeonato**: o placar do dia da ESPN em que o jogo começa (a ESPN agrupa os dias no horário de Nova York; um jogo às 00:30 UTC de 03/12 está no dia 02/12). Ele já traz os gols com autor e minuto. Se o jogo não estiver nesse dia, os dias vizinhos são consultados. Com gzip, a resposta tem ~1–9 KB conforme o número de jogos do dia.
- **O que avisar** (`domain/MatchEvents.kt`): início, quando o jogo passa de agendado para em andamento; gol, quando o placar de um time sobe durante o jogo ou logo no apito final. Vários gols entre duas consultas saem em avisos separados, cada um com o placar parcial. Gol anulado (placar que diminui) não avisa. Um jogo inteiro perdido entre duas consultas também não gera avisos atrasados. Ao acompanhar, o estado atual é guardado, então só o que acontecer dali em diante vira aviso.
- **Durante os jogos:** o `LiveMatchService` (serviço em primeiro plano do tipo `dataSync`) consulta a cada 30 s com a bola rolando e a cada 1 min antes do apito inicial e no intervalo (`MatchEvents.pollInterval`), e mostra uma notificação fixa e silenciosa com os placares. Com a opção **Economia de bateria** (Configurações › Notificações), os intervalos passam a 2 min e 3 min. Ele se encerra sozinho quando nenhum jogo acompanhado está na janela. No Android 15+, esse tipo de serviço tem limite de 6 h por dia; ao esgotar, o app segue pelo WorkManager.
- **Para acordar no início:** um alarme exato 2 min antes do próximo jogo acompanhado. Com a permissão "Alarmes e lembretes", o alarme também autoriza iniciar o serviço com o app em segundo plano. O app explica a permissão uma vez, ao acompanhar o primeiro jogo, e mostra o estado dela nas Configurações.
- **Sem alarme exato,** ou quando o Android não deixa iniciar o serviço: o `WatchWorker` consulta em laço, no mínimo a cada 1 min, e o sistema pode espaçar isso com o aparelho em repouso.
- **Trabalho periódico** (rede de segurança e jogos do time do coração), só quando há o que acompanhar: a cada 15 min se algum jogo acompanhado começa nas próximas 6 h; a cada 6 h se há jogos mais distantes ou o aviso do time do coração está ligado; nenhum caso contrário.
- **Consumo estimado** (não medido em aparelho): ~0,1–1 MB por hora de jogo acompanhado. Fora dos jogos, só o trabalho periódico. Na bateria, o maior custo é acordar o rádio a cada consulta, principalmente em 4G/5G.
- Alarmes são reagendados ao reiniciar o aparelho, ao atualizar o app e quando a permissão de alarmes muda.
- **Teste:** o build de debug tem um comando adb que "rebobina" um jogo real encerrado para testar os avisos (`app/src/debug/.../DebugCommandReceiver.kt`).

## 6. Arquitetura

- Módulo único `:app`, pacote `io.github.alcindogandhi.champs`.
- Injeção de dependências manual (`AppContainer` em `ChampsApp`).
- ViewModels com `StateFlow`. O `Loader` (`ui/common/DataState.kt`) liga um fluxo do repositório ao estado da tela (dado, carregando, atualizando, erro) e cancela a carga anterior a cada nova.
- Rede: OkHttp e kotlinx.serialization com campos opcionais e `ignoreUnknownKeys`, para tolerar mudanças na API. Escudos carregados pelo Coil, com cache em disco.
- Persistência: Room para as simulações (`simulation`, `simulated_score`) e os jogos acompanhados (`followed_match`), e DataStore para as preferências.
- Notificações: serviço em primeiro plano, `AlarmManager` e WorkManager (§5.7).
- Navegação: Navigation Compose com rotas tipadas (`ui/AppRoot.kt`).

## 7. Privacidade

O app não coleta nem envia dados pessoais (ver [PRIVACY.md](PRIVACY.md)). Os únicos pedidos de rede vão para a ESPN e para o ge (dados de futebol e escudos), com um User-Agent genérico do app.

## 8. Limitações conhecidas

- As APIs da ESPN e do ge não são oficiais: podem mudar ou deixar de responder sem aviso. No ge, o formato v2 depende de dados embutidos na página, o que é mais frágil.
- Campeonatos do ge não têm artilharia nem detalhes do jogo; no formato v2, só a rodada atual das fases de liga.
- Alguns textos da ESPN chegam misturados com inglês mesmo em português (ex.: "PAL win 5-4 nos pênaltis").
- As zonas do catálogo são aproximadas (§3).
- O número da rodada é deduzido (§5.2). Se a ESPN mudar a forma de criar os ids, as rodadas podem sair erradas, e os testes com fixtures reais devem acusar.
