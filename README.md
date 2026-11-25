# Champs

App Android de estatísticas de futebol, com foco nos campeonatos brasileiros: tabela, jogos por rodada, mata-mata, artilheiros, jogos do dia, lance a lance, escalações, comparação de estatísticas, time do coração e um simulador de pontos corridos. É software livre e não tem anúncios.

O Champs é inspirado no Kamps, app descontinuado em 2019. Ele recria as funcionalidades, mas não usa nome, marca, imagens nem código do original.

A especificação completa está em [SPEC.md](SPEC.md).

## Dados

Os dados vêm da API pública e não oficial da ESPN (`site.api.espn.com`), sem chave e sem backend próprio. O Champs não tem vínculo com a ESPN. Todo o acesso passa pela interface `FootballDataSource` (`data/FootballRepository.kt`), então outra fonte pode substituir a ESPN sem mexer nas telas.

## Idiomas

Português do Brasil (padrão, `res/values`) e inglês (`res/values-en`). O app segue o idioma do aparelho. No Android 13 ou mais novo, o idioma do app também pode ser escolhido em Configurações › Apps › Champs › Idioma. Os textos que vêm da ESPN (narração, status, observações) acompanham o idioma do app: português ou, para os demais idiomas, inglês.

Para testar outro idioma no emulador sem mudar o sistema:

```sh
adb shell cmd locale set-app-locales io.github.alcindogandhi.champs --locales en-US   # "" volta ao do sistema
```

## Requisitos

- Android 10 (API 29) ou mais novo no aparelho.
- JDK 17 ou mais novo e Android SDK com a plataforma 37 para compilar. O Gradle é baixado pelo wrapper.

## Compilar e instalar

```sh
./gradlew assembleDebug        # APK de debug
./gradlew installDebug         # instala no aparelho/emulador conectado
./gradlew assembleRelease      # APK de release (minificado)
./gradlew bundleRelease        # AAB de release, formato exigido pela Play Store
```

Os builds de release só saem assinados se existir um `keystore.properties` na raiz (ignorado pelo git). Copie `keystore.properties.example` e preencha o caminho da chave e as senhas.

## Testes

```sh
./gradlew testDebugUnitTest          # unitários (JVM): leitura da ESPN, rodadas, mata-mata, tabela, escalação
./gradlew connectedDebugAndroidTest  # instrumentados (emulador/aparelho): componentes de tela
./gradlew lintDebug
```

Os testes unitários usam respostas reais da ESPN gravadas em `app/src/test/resources/espn/`.

## Scripts

| Script | Para que serve |
|---|---|
| `scripts/check_slugs.py` | Confere se os campeonatos do catálogo respondem na ESPN e lista as fases do ano: `python3 -I scripts/check_slugs.py 2026 bra.1 bra.2` |
| `scripts/season_fixture.py` | Grava a temporada inteira de um campeonato como fixture de teste: `python3 -I scripts/season_fixture.py bra.1 2026 saida.json` |
| `scripts/store_screenshots.sh` | Gera as capturas da loja em `fastlane/` com um emulador ligado |
| `scripts/art/` | Fontes SVG do ícone e do feature graphic da loja |

## Estrutura

```
app/src/main/java/io/github/alcindogandhi/champs/
  catalog/          campeonatos oferecidos (slug da ESPN, nome, região, zonas da tabela)
  data/remote/      cliente HTTP com cache em arquivo, DTOs e conversão da ESPN
  data/             FootballDataSource / EspnDataSource
  data/db/          Room: simulações
  data/settings/    DataStore: tema, campeonatos seguidos, time do coração, colunas
  domain/           modelos, SeasonBuilder (rodadas e chaves), StandingsCalculator, LineupLayout
  ui/               telas em Jetpack Compose
```

## Licença

[GPL-3.0](LICENSE).
