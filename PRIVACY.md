# Política de privacidade — Champs

*Vigente desde 25 de novembro de 2026.* [English version below](#privacy-policy--champs).

O Champs (`io.github.alcindogandhi.champs`) é um app de código aberto, mantido por Alcindo Gandhi, que mostra estatísticas de campeonatos de futebol. Ele **não coleta, não envia e não compartilha** nenhum dado pessoal. O app não exige conta e não contém anúncios nem bibliotecas de análise ou rastreamento.

## Acesso à internet

O app usa a internet só para buscar dados públicos de futebol (tabelas, jogos, escalações, escudos) na API pública da ESPN. Esses pedidos levam apenas o campeonato, o jogo ou o time consultado, o idioma do app e um identificador genérico do app (User-Agent). Nenhum dado seu é enviado. Como em qualquer acesso à internet, a ESPN recebe o endereço IP da conexão. O Champs não tem vínculo com a ESPN.

## Notificações

Os avisos de início e gols são gerados no próprio aparelho: o app consulta a ESPN durante os jogos que você acompanha. Nenhum serviço de notificações de terceiros (como o Firebase) é usado, e nenhum identificador do aparelho é enviado. Durante esses jogos, uma notificação fixa mostra que o app está acompanhando. A permissão "Alarmes e lembretes" serve só para o app acordar na hora do jogo.

## Dados guardados no aparelho

Tudo abaixo fica armazenado apenas no seu aparelho:

- **Preferências:** campeonatos seguidos, time do coração, tema e colunas da tabela.
- **Simulações:** os nomes das simulações e os placares que você escolheu.
- **Jogos acompanhados:** os jogos com aviso ligado e o último placar visto de cada um.
- **Cache:** cópias das últimas respostas da ESPN e dos escudos, para o app abrir rápido e funcionar sem internet.

## Backup do Android

Se o backup do Android estiver ativado no aparelho, as preferências e as simulações podem ser incluídas no backup da sua conta Google, que é feito pelo sistema e controlado por você. O cache não entra no backup. O desenvolvedor não tem acesso a esse backup.

## Como apagar seus dados

Use Configurações › Apagar todos os dados, dentro do app, ou Configurações do Android › Apps › Champs › Armazenamento › Limpar dados. Desinstalar o app também apaga todos os dados.

## Crianças

O app não é direcionado a crianças e não coleta dados de ninguém.

## Alterações e contato

Alterações nesta política serão publicadas nesta página, com nova data de vigência. Dúvidas podem ser enviadas pelas [issues do projeto no GitHub](https://github.com/alcindogandhi/champs/issues).

---

# Privacy policy — Champs

*Effective November 25, 2026.*

Champs (`io.github.alcindogandhi.champs`) is an open-source app, maintained by Alcindo Gandhi, that shows football (soccer) competition statistics. It **does not collect, transmit or share** any personal data. The app requires no account and contains no ads, analytics or tracking libraries.

## Internet access

The app uses the internet only to fetch public football data (standings, matches, lineups, crests) from ESPN's public API. These requests carry only the competition, match or team being looked up, the app language and a generic app identifier (User-Agent). None of your data is sent. As with any internet access, ESPN receives the IP address of the connection. Champs is not affiliated with ESPN.

## Notifications

Kick-off and goal alerts are generated on the device itself: the app checks ESPN during the matches you follow. No third-party notification service (such as Firebase) is used, and no device identifier is sent. During those matches, an ongoing notification shows that the app is following them. The "Alarms & reminders" permission is used only to wake the app up at kick-off.

## Data stored on the device

Everything below is stored only on your device:

- **Preferences:** followed competitions, favorite team, theme and table columns.
- **Simulations:** simulation names and the scores you chose.
- **Followed matches:** the matches with alerts on and the last score seen for each.
- **Cache:** copies of the latest ESPN responses and crests, so the app opens quickly and works offline.

## Android backup

If Android backup is turned on, your preferences and simulations may be included in your Google account backup, which is made by the system and controlled by you. The cache is not backed up. The developer has no access to that backup.

## Deleting your data

Use Settings › Delete all data inside the app, or Android Settings › Apps › Champs › Storage › Clear data. Uninstalling the app also deletes all of its data.

## Children

The app is not directed at children and collects no data from anyone.

## Changes and contact

Changes to this policy will be published on this page with a new effective date. Questions can be sent through the [project's GitHub issues](https://github.com/alcindogandhi/champs/issues).
