import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useQuizSocket } from '@/hooks/useQuizSocket';
import { Question, AnswerResult, Player, Leaderboard } from '@/types/quiz';
import { Trophy, Clock, Zap } from 'lucide-react';
import estabelecimentoImg from '@/assets/estabelecimento.jpg';

export default function QuizPlayer() {
  const { sessionCode } = useParams<{ sessionCode: string }>();
  const [nickname, setNickname] = useState('');
  const [isJoined, setIsJoined] = useState(false);
  const [player, setPlayer] = useState<Player | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<Question | null>(null);
  const [selectedOption, setSelectedOption] = useState<number | null>(null);
  const [answerResult, setAnswerResult] = useState<AnswerResult | null>(null);
  const [leaderboard, setLeaderboard] = useState<Leaderboard | null>(null);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [isGameStarted, setIsGameStarted] = useState(false);
  const [isGameFinished, setIsGameFinished] = useState(false);
  const [isQuestionLocked, setIsQuestionLocked] = useState(false);
  const [winnerMessage, setWinnerMessage] = useState<string | null>(null);
  const questionStartTime = useRef<number>(0);

  const { subscribe, send, isConnected } = useQuizSocket({
    onConnect: () => console.log('Player conectado ao WebSocket'),
  });

  // Join na sessão
  const handleJoin = () => {
    if (!nickname.trim() || !sessionCode) return;

    send('/app/quiz/join', {
      nickname: nickname.trim(),
      sessionCode: sessionCode.toUpperCase(),
    });
  };

  // Responder pergunta
  const handleAnswer = (optionIndex: number) => {
    if (!currentQuestion || selectedOption !== null) return;

    const responseTime = Date.now() - questionStartTime.current;
    setSelectedOption(optionIndex);

    send('/app/quiz/answer', {
      questionId: currentQuestion.questionId,
      selectedOption: optionIndex,
      responseTimeMs: responseTime,
    });
  };

  // Resetar estados quando nova pergunta chega
  useEffect(() => {
    if (currentQuestion) {
      setSelectedOption(null);
      setAnswerResult(null);
      setIsQuestionLocked(false);
      setWinnerMessage(null);
    }
  }, [currentQuestion?.questionId]);

  // Timer da pergunta
  useEffect(() => {
    if (!currentQuestion || selectedOption !== null) {
      setTimeLeft(null);
      return;
    }

    setTimeLeft(currentQuestion.timeLimit);
    questionStartTime.current = Date.now();

    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev === null || prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [currentQuestion, selectedOption]);

  // Subscribe aos eventos
  useEffect(() => {
    if (!isConnected) return;

    // Resposta do join
    const unsubJoinResponse = subscribe('/user/queue/quiz/join-response', (data: Player) => {
      console.log('[Player] Join confirmado:', data);
      setPlayer(data);
      setIsJoined(true);
    });

    // Nova pergunta
    const unsubQuestion = subscribe(
      `/topic/quiz/${sessionCode}/question`,
      (data: Question) => {
        console.log('[Player] Nova pergunta:', data);
        setCurrentQuestion(data);
        setSelectedOption(null);
        setAnswerResult(null);
        setIsQuestionLocked(false);
        setWinnerMessage(null);
        setIsGameStarted(true);
      }
    );

    // Resultado da resposta
    const unsubAnswerResult = subscribe(
      '/user/queue/quiz/answer-result',
      (data: AnswerResult) => {
        console.log('[Player] Resultado da resposta:', data);
        setAnswerResult(data);
        if (player) {
          setPlayer({ ...player, score: data.totalScore, rank: data.rank });
        }
      }
    );

    // Atualização do ranking
    const unsubLeaderboard = subscribe(
      `/topic/quiz/${sessionCode}/leaderboard`,
      (data: Leaderboard) => {
        console.log('[Player] Ranking atualizado:', data);
        setLeaderboard(data);
      }
    );

    // Jogo iniciado
    const unsubStart = subscribe(`/topic/quiz/${sessionCode}/start`, () => {
      console.log('[Player] Jogo iniciado');
      setIsGameStarted(true);
    });

    // Jogo finalizado
    const unsubEnd = subscribe(`/topic/quiz/${sessionCode}/end`, (data: Leaderboard) => {
      console.log('[Player] Jogo finalizado');
      setLeaderboard(data);
      setIsGameFinished(true);
      setCurrentQuestion(null);
    });

    // Pergunta bloqueada (alguém acertou primeiro)
    const unsubQuestionLocked = subscribe(
      `/topic/quiz/${sessionCode}/question-locked`,
      (data: { winnerNickname?: string; pointsEarned?: number; message?: string }) => {
        console.log('[Player] Pergunta bloqueada:', data);
        setIsQuestionLocked(true);
        if (data?.message) setWinnerMessage(data.message);
      }
    );

    return () => {
      unsubJoinResponse();
      unsubQuestion();
      unsubAnswerResult();
      unsubLeaderboard();
      unsubStart();
      unsubEnd();
      unsubQuestionLocked();
    };
  }, [isConnected, sessionCode, subscribe, player]);

  // Tela de entrada (nickname)
  if (!isJoined) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-forest-dark via-background to-coral-accent/10 flex items-center justify-center p-4">
        <Card className="p-8 max-w-md w-full space-y-6 relative overflow-hidden border-coral-accent/20">
          <div className="absolute top-0 left-0 right-0 h-40">
            <img
              src={estabelecimentoImg}
              alt="Villa Custom"
              className="w-full h-full object-cover object-center"
              style={{
                maskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)',
                WebkitMaskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)'
              }}
            />
          </div>
          <div className="text-center space-y-3 relative z-10 pt-28">
            <h1 className="text-3xl font-bold text-coral-accent">QUIZ</h1>
            <p className="text-cream/80">Sessão: {sessionCode?.toUpperCase()}</p>
          </div>

          <div className="space-y-4">
            <div>
              <label className="text-sm font-medium">Seu Nome</label>
              <Input
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="Digite seu nickname"
                maxLength={50}
                className="mt-1"
                onKeyDown={(e) => e.key === 'Enter' && handleJoin()}
              />
            </div>

            <Button
              onClick={handleJoin}
              disabled={!nickname.trim() || !isConnected}
              className="w-full bg-coral-accent hover:bg-coral-accent/90 text-forest-dark"
              size="lg"
            >
              {isConnected ? 'Entrar no Quiz' : 'Conectando...'}
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  // Aguardando início do jogo
  if (!isGameStarted) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-forest-dark via-background to-coral-accent/10 flex items-center justify-center p-4">
        <Card className="p-8 max-w-md w-full text-center space-y-6 border-coral-accent/20">
          <div>
            <h1 className="text-3xl font-bold mb-2 text-coral-accent">Bem-vindo, {player?.nickname}!</h1>
            <p className="text-cream/70">Aguardando o quiz começar...</p>
          </div>
          <div className="animate-pulse">
            <Trophy className="w-20 h-20 mx-auto text-coral-accent" />
          </div>
        </Card>
      </div>
    );
  }

  // Jogo finalizado
  if (isGameFinished && leaderboard) {
    const playerRank = leaderboard.players.find((p) => p.id === player?.id);

    return (
      <div className="min-h-screen bg-gradient-to-br from-forest-dark via-background to-coral-accent/10 flex items-center justify-center p-4">
        <Card className="p-8 max-w-md w-full space-y-6 border-coral-accent/20">
          <div className="text-center">
            <Trophy className="w-16 h-16 mx-auto mb-4 text-coral-accent card-glow" />
            <h1 className="text-3xl font-bold mb-2 text-coral-accent">Quiz Finalizado!</h1>
          </div>

          {playerRank && (
            <Card className="p-6 bg-coral-accent/10 border-2 border-coral-accent card-glow">
              <div className="text-center">
                <p className="text-sm text-cream/70">Sua Posição</p>
                <p className="text-5xl font-bold my-2 text-coral-accent">{playerRank.rank}°</p>
                <p className="text-2xl font-bold text-cream">{playerRank.score} pontos</p>
                <p className="text-sm text-cream/70 mt-2">
                  {playerRank.correctAnswers} acertos
                </p>
              </div>
            </Card>
          )}

          <div>
            <h3 className="font-semibold mb-3 text-coral-accent">Ranking Final</h3>
            <div className="space-y-2">
              {leaderboard.players.slice(0, 5).map((p, index) => (
                <div
                  key={p.id}
                  className={`flex items-center justify-between p-3 rounded-lg ${
                    p.id === player?.id
                      ? 'bg-coral-accent/20 border-2 border-coral-accent'
                      : 'bg-secondary border border-coral-accent/10'
                  }`}
                >
                  <span className="font-semibold text-cream">
                    {index + 1}. {p.nickname}
                  </span>
                  <span className="font-bold text-cream">{p.score}</span>
                </div>
              ))}
            </div>
          </div>
        </Card>
      </div>
    );
  }

  // Jogando: mostrando pergunta
  if (currentQuestion) {
    const showResult = answerResult !== null; // feedback textual ao jogador

    return (
      <div className="min-h-screen bg-gradient-to-br from-forest-dark via-background to-coral-accent/10 p-4">
        <div className="max-w-2xl mx-auto space-y-4 pt-8">
          {/* Header com timer e pontuação */}
          <Card className="p-4 border-coral-accent/20">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Clock className="w-5 h-5 text-cream" />
                <span className="text-2xl font-bold text-cream">
                  {timeLeft !== null ? `${timeLeft}s` : '--'}
                </span>
              </div>
              <div className="text-right">
                <p className="text-sm text-cream/70">Sua Pontuação</p>
                <p className="text-2xl font-bold text-cream">{player?.score || 0}</p>
              </div>
            </div>
          </Card>

          {/* Pergunta */}
          <Card className="p-6 border-coral-accent/20">
            <div className="mb-4">
              <p className="text-sm text-cream/70">
                Pergunta {currentQuestion.questionNumber} de {currentQuestion.totalQuestions}
              </p>
            </div>
            <h2 className="text-2xl font-bold mb-6 text-coral-accent">{currentQuestion.question}</h2>

            {/* Opções */}
            <div className="grid grid-cols-1 gap-3">
              {currentQuestion.options.map((option, index) => {
                let bgClass = 'bg-secondary hover:bg-secondary/90';
                let textClass = 'text-cream';
                let borderClass = 'border-coral-accent/10';

                // Só revela a correta para todos quando chegar o lock
                if (isQuestionLocked && answerResult && typeof answerResult.correctOption === 'number') {
                  if (index === answerResult.correctOption) {
                    bgClass = 'bg-green-500 hover:bg-green-600';
                    textClass = 'text-white';
                    borderClass = 'border-green-400';
                  } else if (selectedOption !== null && index === selectedOption && !answerResult.correct) {
                    bgClass = 'bg-red-500 hover:bg-red-600';
                    textClass = 'text-white';
                    borderClass = 'border-red-400';
                  } else {
                    bgClass = 'bg-secondary/60';
                    textClass = 'text-cream/60';
                    borderClass = 'border-coral-accent/5';
                  }
                } else if (showResult && selectedOption !== null) {
                  // Feedback do jogador: verde se acertou, vermelho se errou, sem revelar a correta para todos
                  if (answerResult?.correct && index === selectedOption) {
                    bgClass = 'bg-green-500 hover:bg-green-600';
                    textClass = 'text-white';
                    borderClass = 'border-green-400';
                  } else if (!answerResult?.correct && index === selectedOption) {
                    bgClass = 'bg-red-500 hover:bg-red-600';
                    textClass = 'text-white';
                    borderClass = 'border-red-400';
                  } else {
                    // demais opções seguem neutras
                  }
                } else if (selectedOption === index) {
                  bgClass = 'bg-coral-accent card-glow';
                  textClass = 'text-forest-dark';
                  borderClass = 'border-coral-accent';
                }

                return (
                  <Button
                    key={index}
                    onClick={() => handleAnswer(index)}
                    disabled={selectedOption !== null || isQuestionLocked}
                    className={`h-auto py-4 text-lg font-semibold transition-all border ${bgClass} ${textClass} ${borderClass}`}
                  >
                    {option}
                  </Button>
                );
              })}
            </div>

            {/* Mensagem de bloqueio */}
            {isQuestionLocked && !showResult && (
              <div className="mt-6 text-center">
                <div className="p-4 bg-coral-accent/20 border-2 border-coral-accent rounded-lg card-glow">
                  <p className="text-xl font-bold text-coral-accent">🏆 {winnerMessage}</p>
                  <p className="text-sm text-cream/70 mt-2">
                    Aguardando próxima pergunta...
                  </p>
                </div>
              </div>
            )}

            {/* Resultado */}
            {showResult && (
              <div className="mt-6 text-center">
                {answerResult.correct ? (
                  <div className="space-y-2">
                    <p className="text-2xl font-bold text-green-500 flex items-center justify-center gap-2">
                      <Zap className="w-6 h-6" />
                      Correto! +{answerResult.pointsEarned} pontos
                    </p>
                    {player?.rank && (
                      <p className="text-sm text-cream/70">
                        Você está em {player.rank}° lugar
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="text-2xl font-bold text-red-500">Errou!</p>
                )}
              </div>
            )}
          </Card>
        </div>
      </div>
    );
  }

  // Aguardando próxima pergunta
  return (
    <div className="min-h-screen bg-gradient-to-br from-forest-dark via-background to-coral-accent/10 flex items-center justify-center p-4">
      <Card className="p-8 max-w-md w-full text-center space-y-6 border-coral-accent/20">
        <p className="text-xl text-cream">Aguardando próxima pergunta...</p>
        <div className="animate-pulse">
          <Clock className="w-16 h-16 mx-auto text-coral-accent" />
        </div>
      </Card>
    </div>
  );
}
