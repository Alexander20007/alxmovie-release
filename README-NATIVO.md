# ALXmovies nativo (Kotlin + Jetpack Compose)

## Como compilar (pelo celular, igual antes)
1. No repositório do GitHub, **substitua o conteúdo** de `.github/workflows/android-apk.yml` pelo arquivo `android-apk.yml` que veio junto.
2. Suba o arquivo `alxmovies-native.zip` na raiz do repositório (pode apagar o zip antigo).
3. O Actions compila e publica `ALXmovies.apk` em Releases. Mesmo `applicationId` e mesma chave de teste do app antigo: instala por cima.

## Etapa 1 (esta entrega)
- Login, cadastro, recuperar senha e convidado (nova sessão)
- Perfis: escolher, PIN, horário permitido (hora do servidor), criar perfil
- Início: destaques, continuar assistindo, assistidos, "porque você assistiu", em alta, populares e gêneros, com filtro de classificação do perfil

## Etapa 2: Detalhes + Player
- **Detalhes:** banner, classificação indicativa, nota/ano/duração, gêneros, sinopse, favoritar (minha lista), compartilhar, temporadas e episódios (séries), títulos parecidos, bloqueio por classificação do perfil
- **Player:** o mesmo player nativo (Media3/ExoPlayer) do app antigo, em `AlxPlayerActivity.java`: gestos, PiP, velocidade, legendas, qualidade, servidores
- **Fluxo de vídeo** (`data/Stream.kt`): API principal + API reserva, troca de servidor, próximo episódio automático, retoma de onde parou e salva o progresso no Supabase (`ui/watch/WatchViewModel.kt`)

## Etapa 3: o resto do app
- **Busca** (filmes/séries com filtro de gênero + aba de canais) e **Minha lista** (favoritos, continuar, assistidos, conquistas)
- **Canais ao vivo**: categorias, só os que estão no ar, favoritos, guia de programação e player em tela cheia
- **Detalhes completos**: avaliar, trailer (YouTube), marcar como visto, comentários com curtidas e respostas
- **Salas** (assistir junto): criar/entrar (com senha), player sincronizado com o anfitrião, chat, fila, trocar anfitrião, maratona de séries
- **Amigos, mensagens e avisos**: código de amigo, sugestões, pedidos, conversa (título/sala indicados), "assistindo agora", avisos de continuar/sequência
- **Laura IA**: recomenda filmes, séries, canais e salas
- **Minha conta**: dados, senha, privacidade de amigos, atividade, assinatura, canais favoritos, convidado (criar conta e código de recuperação)
- **Perfis**: criar/editar/apagar, foto, PIN, classificação, horário de uso, gêneros favoritos
- **Atualização**: avisa quando há versão nova nas Releases do GitHub

## Ainda não portado
Baixar para assistir offline, notificações push (Firebase), página de pessoa/elenco, histórico por episódio. A sincronização das salas e do chat usa consultas a cada poucos segundos (sem WebSocket).
