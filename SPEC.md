# Champs — Especificação

Documento de referência do comportamento e das decisões técnicas do app. Para compilar, testar e contribuir, veja o [README](README.md).

## 1. Visão geral

App Android de estatísticas de futebol, com foco nos campeonatos brasileiros, que recria as funcionalidades do Kamps (descontinuado em 2019).

- **Plataforma:** Android 10+ (`minSdk 29`), Kotlin, Jetpack Compose com Material 3.
- **Dados:** API pública e não oficial da ESPN, sem chave e sem backend próprio (§4).
- **Sem anúncios, sem conta, sem rastreamento.** Nenhuma biblioteca proprietária (Google Play Services, Firebase), então o app é compatível com o F-Droid. A única permissão é `INTERNET`.
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
| F10 | Offline | Toda resposta da ESPN fica em cache. As telas abrem com o último dado conhecido e atualizam em seguida. Sem rede, um aviso mostra de quando é o dado. "Puxar para atualizar" em todas as listas. |

**Fora do escopo da v1:** notificações (gols e início de jogos), widgets, notícias e simulação de mata-mata.

## 3. Catálogo de campeonatos

Definido em `catalog/CompetitionCatalog.kt`. Cada campeonato tem o slug da ESPN, o nome, a região, se pode ser simulado e as zonas da tabela.

- **Brasil:** Série A e Série B (simuláveis), Copa do Brasil, Copa do Nordeste, Paulista, Carioca, Mineiro e Gaúcho.
- **América do Sul:** Libertadores, Sul-Americana e Campeonato Argentino.
- **Europa:** Champions League, Europa League e Conference League. Premier League, La Liga, Serie A, Bundesliga, Ligue 1, Liga Portugal e Eredivisie (simuláveis).
- **Mundo:** Copa do Mundo e MLS.

Séries C e D e os demais estaduais não entram porque a ESPN não tem dados deles (conferido com `scripts/check_slugs.py`).

**Zonas:** quando a ESPN informa a faixa de um time (`note` com cor e descrição, como nos grupos da Libertadores), o app usa essa informação. Senão, usa as zonas aproximadas do catálogo (G4/G6, Sul-Americana, acesso, rebaixamento), e a legenda avisa que o número de vagas pode mudar a cada temporada.

## 4. Fonte de dados (ESPN)

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

## 6. Arquitetura

- Módulo único `:app`, pacote `io.github.alcindogandhi.champs`.
- Injeção de dependências manual (`AppContainer` em `ChampsApp`).
- ViewModels com `StateFlow`. O `Loader` (`ui/common/DataState.kt`) liga um fluxo do repositório ao estado da tela (dado, carregando, atualizando, erro) e cancela a carga anterior a cada nova.
- Rede: OkHttp e kotlinx.serialization com campos opcionais e `ignoreUnknownKeys`, para tolerar mudanças na API. Escudos carregados pelo Coil, com cache em disco.
- Persistência: Room para as simulações (`simulation`, `simulated_score`) e DataStore para as preferências.
- Navegação: Navigation Compose com rotas tipadas (`ui/AppRoot.kt`).

## 7. Privacidade

O app não coleta nem envia dados pessoais (ver [PRIVACY.md](PRIVACY.md)). Os únicos pedidos de rede vão para a ESPN (dados de futebol e escudos), com um User-Agent genérico do app.

## 8. Limitações conhecidas

- A API da ESPN não é oficial: pode mudar ou deixar de responder sem aviso.
- Alguns textos da ESPN chegam misturados com inglês mesmo em português (ex.: "PAL win 5-4 nos pênaltis").
- As zonas do catálogo são aproximadas (§3).
- O número da rodada é deduzido (§5.2). Se a ESPN mudar a forma de criar os ids, as rodadas podem sair erradas, e os testes com fixtures reais devem acusar.
