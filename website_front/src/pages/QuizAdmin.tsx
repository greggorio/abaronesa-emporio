import { useState, useEffect, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useQuizSocket } from '@/hooks/useQuizSocket';
import { quizApi } from '@/lib/quiz-api';
import { QuizSession, Leaderboard, Question, Category } from '@/types/quiz';
import { Users, Play, SkipForward, Trophy } from 'lucide-react';
import estabelecimentoImg from '@/assets/estabelecimento.jpg';

export default function QuizAdmin() {
  const [session, setSession] = useState<QuizSession | null>(null);
  const [leaderboard, setLeaderboard] = useState<Leaderboard | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<Question | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [numberOfQuestions, setNumberOfQuestions] = useState<number>(10);
  const [totalQuestions, setTotalQuestions] = useState<number>(25);
  const [suggestedQuestions, setSuggestedQuestions] = useState<number>(10);
  const [autoAdvance, setAutoAdvance] = useState<boolean>(true);
  const [highlightedPlayer, setHighlightedPlayer] = useState<string | null>(null);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [prize, setPrize] = useState<string>('');
  // Estado local para timer e guardas contra avanços duplicados
  const currentQuestionRef = useRef<Question | null>(null);
  const advanceTimeoutRef = useRef<number | null>(null);
  const revealedQuestionsSet = useRef<Set<number>>(new Set()); // rastreia perguntas já reveladas

  const { subscribe, isConnected } = useQuizSocket({
    onConnect: () => console.log('Admin conectado ao WebSocket'),
  });

  const gradientBackground =
    'bg-gradient-to-br from-[hsl(var(--card))] via-[hsl(var(--background))] to-[hsl(var(--accent)/0.15)]';
  const accentText = 'text-[hsl(var(--accent))]';
  const accentForegroundText = 'text-[hsl(var(--accent-foreground))]';
  const accentBorder = 'border border-[hsl(var(--accent)/0.25)]';
  const accentBorderSoft = 'border border-[hsl(var(--accent)/0.15)]';
  const accentBg = 'bg-[hsl(var(--accent))]';
  const accentBgSoft = 'bg-[hsl(var(--accent)/0.2)]';
  const primaryText = 'text-[hsl(var(--foreground))]';
  const primaryTextMuted = 'text-[hsl(var(--foreground)/0.7)]';
  const primaryTextSoft = 'text-[hsl(var(--foreground)/0.55)]';
  const secondaryBg = 'bg-[hsl(var(--secondary))]';
  const buttonPrimary = 'bg-[hsl(var(--button-primary-bg))] text-[hsl(var(--button-primary-text))]';
  const buttonPrimaryHover = 'hover:bg-[hsl(var(--button-primary-bg)/0.9)]';

  // Sem timers globais

  // Calcular opções de quantidade de perguntas baseado na categoria selecionada
  const getQuestionOptions = (): number[] => {
    let availableQuestions = totalQuestions;
    if (selectedCategory && selectedCategory !== 'ALL') {
      const category = categories.find(cat => cat.name === selectedCategory);
      if (category) availableQuestions = category.questionCount;
    }
    if (availableQuestions === 0) return [];
    const percentages = [0.25, 0.5, 0.75, 1.0];
    const options = percentages
      .map(p => Math.max(3, Math.floor(availableQuestions * p)))
      .filter(n => n <= availableQuestions);
    return Array.from(new Set(options)).sort((a, b) => a - b).slice(0, 4);
  };

  // Carregar estatísticas e categorias ao montar
  useEffect(() => {
    quizApi.getStats().then((stats) => {
      setTotalQuestions(stats.totalQuestions);
      setSuggestedQuestions(stats.suggestedQuestions);
      setNumberOfQuestions(stats.suggestedQuestions);
    }).catch((error) => {
      console.error('Erro ao carregar estatísticas:', error);
    });

    quizApi.getCategories().then((cats) => {
      setCategories(cats);
      // Recalcula total apenas com categorias ativas para não contar perguntas de categorias inativas
      const activeTotal = cats.filter((c) => c.active).reduce((sum, c) => sum + (c.questionCount || 0), 0);
      if (activeTotal > 0) {
        setTotalQuestions(activeTotal);
        setSuggestedQuestions((prev) => Math.min(prev, activeTotal));
        setNumberOfQuestions((prev) => Math.min(prev, activeTotal));
      }
    }).catch((error) => {
      console.error('Erro ao carregar categorias:', error);
    });
  }, []);

  // Atualizar numberOfQuestions quando a categoria mudar
  useEffect(() => {
    const options = getQuestionOptions();
    if (options.length > 0) {
      const defaultOption = options[Math.floor(options.length / 2)] || options[0];
      setNumberOfQuestions(defaultOption);
    }
  }, [selectedCategory, categories, totalQuestions]);

  // Criar nova sessão
  const handleCreateSession = async () => {
    setIsCreating(true);
    try {
      const newSession = await quizApi.createSession({
        numberOfQuestions,
        questionTimeLimit: 30,
        autoAdvance,
        category: selectedCategory && selectedCategory !== 'ALL' ? selectedCategory : undefined,
      });
      setSession(newSession);

      // Carregar ranking inicial
      const initialLeaderboard = await quizApi.getLeaderboard(newSession.sessionCode);
      setLeaderboard(initialLeaderboard);
    } catch (error) {
      console.error('Erro ao criar sessão:', error);
      alert('Erro ao criar sessão. Verifique se o backend está rodando.');
    } finally {
      setIsCreating(false);
    }
  };

  // Iniciar quiz
  const handleStartQuiz = async () => {
    if (!session) return;

    try {
      const question = await quizApi.startSession(session.sessionCode);
      setCurrentQuestion(question);
      setSession({ ...session, status: 'ACTIVE' });
    } catch (error) {
      console.error('Erro ao iniciar quiz:', error);
      alert('Erro ao iniciar quiz');
    }
  };

  // Próxima pergunta
  const handleNextQuestion = async () => {
    if (!session) return;

    try {
      const result = await quizApi.nextQuestion(session.sessionCode);

      // Verifica se retornou uma pergunta ou o ranking final
      if ('questionId' in result) {
        setCurrentQuestion(result as Question);
      } else {
        // Jogo terminou
        setLeaderboard(result as Leaderboard);
        setSession({ ...session, status: 'FINISHED' });
        setCurrentQuestion(null);
      }
    } catch (error) {
      console.error('Erro ao avançar pergunta:', error);
    }
  };

  // Timer countdown
  useEffect(() => {
    if (!currentQuestion) {
      setTimeLeft(null);
      return;
    }

    // Inicia timer
    setTimeLeft(currentQuestion.timeLimit);
    // Atualiza referência da pergunta atual
    currentQuestionRef.current = currentQuestion;
    // Ao entrar numa nova pergunta, cancela qualquer auto-advance pendente anterior
    if (advanceTimeoutRef.current) {
      clearTimeout(advanceTimeoutRef.current);
      advanceTimeoutRef.current = null;
    }

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
  }, [currentQuestion]);

  // Reveal correto no timeout (cliente); avanço é agendado no question-locked
  useEffect(() => {
    if (timeLeft === 0 && currentQuestionRef.current && session) {
      // Revela a resposta correta apenas uma vez por pergunta
      // Usa a ref para garantir que revelamos a pergunta que realmente esgotou o tempo
      const questionId = currentQuestionRef.current.questionId;
      if (!revealedQuestionsSet.current.has(questionId)) {
        revealedQuestionsSet.current.add(questionId);
        console.log('[Admin] ⏱️ Tempo esgotado! Revelando correta para pergunta', questionId);
        quizApi.revealCorrect(session.sessionCode).catch((err) => {
          console.error('Erro ao revelar resposta correta:', err);
        });
      }
    }
  }, [timeLeft, session]);

  // Subscribe nos eventos quando tiver uma sessão
  useEffect(() => {
    if (!session || !isConnected) return;

    const unsubscribeLeaderboard = subscribe(
      `/topic/quiz/${session.sessionCode}/leaderboard`,
      (data: Leaderboard) => {
        console.log('[Admin] Ranking atualizado:', data);
        setLeaderboard(data);
      }
    );

    const unsubscribePlayerJoined = subscribe(
      `/topic/quiz/${session.sessionCode}/player-joined`,
      (data: { nickname: string }) => {
        console.log('[Admin] Novo jogador:', data.nickname);
        // Recarregar leaderboard
        if (session) {
          quizApi.getLeaderboard(session.sessionCode).then(setLeaderboard);
        }
      }
    );

    const unsubscribeQuestionLocked = subscribe(
      `/topic/quiz/${session.sessionCode}/question-locked`,
      (data: { winnerNickname?: string; questionId?: number }) => {
        console.log('[Admin] Pergunta bloqueada por:', data.winnerNickname, 'QID:', data.questionId);
        // Destaca o jogador vencedor
        setHighlightedPlayer(data.winnerNickname);
        // Remove destaque após 3 segundos
        setTimeout(() => setHighlightedPlayer(null), 3000);

        // Auto-advance: se ativado, avança automaticamente após 5s
        if (session.autoAdvance) {
          console.log('[Admin] ⚡ Auto-advance ativado! Avançando em 5 segundos...');
          const lockedQuestionId = data.questionId;
          // Só agenda se o lock se refere à pergunta atualmente exibida
          if (!lockedQuestionId || currentQuestionRef.current?.questionId !== lockedQuestionId) {
            console.log('[Admin] ⏸️ Ignorando auto-advance: lock não corresponde à pergunta atual');
            return;
          }
          if (advanceTimeoutRef.current) {
            clearTimeout(advanceTimeoutRef.current);
          }
          advanceTimeoutRef.current = window.setTimeout(() => {
            // Só avança se ainda estivermos na mesma pergunta que foi bloqueada
            if (!lockedQuestionId || currentQuestionRef.current?.questionId !== lockedQuestionId) {
              console.log('[Admin] ⏸️ Ignorando auto-advance (pergunta mudou)');
              return;
            }
            console.log('[Admin] ⏭️ Executando auto-advance');
            handleNextQuestion();
          }, 5000) as unknown as number;
        } else {
          console.log('[Admin] ⏸️ Auto-advance desativado - aguardando clique manual');
        }
      }
    );

    return () => {
      unsubscribeLeaderboard();
      unsubscribePlayerJoined();
      unsubscribeQuestionLocked();
    };
  }, [session, isConnected, subscribe]);

  // Tela inicial: criar sessão
  if (!session) {
    return (
      <div className={`min-h-screen ${gradientBackground} flex items-center justify-center p-4`}>
        <Card className={`p-8 max-w-4xl w-full space-y-5 relative overflow-hidden ${accentBorder}`}>
          <div className="absolute top-0 left-0 right-0 h-32 -mt-0">
            <img
              src={estabelecimentoImg}
              alt="Quiz Arena"
              className="w-full h-full object-cover object-center"
              style={{
                maskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)',
                WebkitMaskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)'
              }}
            />
          </div>
          <div className="text-center space-y-2 relative z-10 pt-24">
            <h1 className={`text-4xl font-bold ${accentText}`}>QUIZ</h1>
            <p className={`text-base ${primaryTextMuted}`}>Crie uma sessão de quiz para seus clientes</p>
          </div>

          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Categoria */}
              <div className="space-y-2">
                <Label htmlFor="category" className="text-base">Categoria</Label>
                <Select value={selectedCategory} onValueChange={setSelectedCategory}>
                  <SelectTrigger className="text-base h-11">
                    <SelectValue placeholder="Todas as categorias" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Todas as categorias</SelectItem>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.name}>
                        {category.name} ({category.questionCount})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Número de Perguntas */}
              <div className="space-y-2">
                <Label className="text-base">Número de Perguntas</Label>
                <div className="grid grid-cols-4 gap-2">
                  {getQuestionOptions().map((option) => (
                  <Button
                    key={option}
                    type="button"
                    variant="outline"
                    size="lg"
                    onClick={() => setNumberOfQuestions(option)}
                    className={`text-xl font-bold h-12 shadow-lg transition-all ${
                      numberOfQuestions === option
                        ? `${accentBg} ${accentForegroundText} border-2 border-[hsl(var(--accent)/0.55)] scale-105 card-glow`
                        : `${secondaryBg} ${primaryText} ${accentBorderSoft} hover:${accentBgSoft} hover:scale-105`
                    }`}
                  >
                    {option}
                  </Button>
                  ))}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-end">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="autoAdvance"
                  checked={autoAdvance}
                  onCheckedChange={(checked) => setAutoAdvance(checked as boolean)}
                />
                <Label htmlFor="autoAdvance" className="text-sm cursor-pointer">
                  Avançar automaticamente
                </Label>
              </div>

              <div className="space-y-2">
                <Label htmlFor="prize" className="text-base">Prêmio (opcional)</Label>
                <Input
                  id="prize"
                  type="text"
                  placeholder="Ex: Cerveja grátis"
                  value={prize}
                  onChange={(e) => setPrize(e.target.value)}
                  className="text-base h-11"
                />
              </div>
            </div>

            <Button
              onClick={handleCreateSession}
              disabled={isCreating || !isConnected}
              size="lg"
              className={`w-full text-xl px-8 py-6 h-auto ${buttonPrimary} ${buttonPrimaryHover} border border-[hsl(var(--border)/0.3)] card-glow`}
            >
              {isCreating ? 'Criando sessão...' : 'Criar Nova Sessão'}
            </Button>
          </div>

          {!isConnected && (
            <p className="text-sm text-center text-yellow-600">Conectando ao servidor...</p>
          )}
        </Card>
      </div>
    );
  }

  // Tela WAITING: mostrar QR code e aguardar jogadores
  if (session.status === 'WAITING') {
    return (
      <div className={`min-h-screen ${gradientBackground} p-8`}>
        <div className="max-w-7xl mx-auto space-y-8">
          {/* Header */}
          <Card className={`p-6 ${accentBorder}`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <img src={estabelecimentoImg} alt="Quiz Arena" className="h-16 w-16 object-cover rounded-lg border border-[hsl(var(--accent)/0.3)]" />
                <div>
                  <h1 className={`text-3xl font-bold ${accentText}`}>Sessão: {session.sessionCode}</h1>
                  <div className="flex items-center gap-3 mt-1">
                    {/* Categoria selecionada (ou Misto) */}
                    <div className="flex items-center gap-2">
                      <span className={`text-sm ${primaryTextMuted}`}>Categoria:</span>
                      <span className={`text-sm font-bold ${accentText}`}>
                        {selectedCategory && selectedCategory !== 'ALL' ? selectedCategory : 'Misto'}
                      </span>
                    </div>

                    {/* Prêmio da rodada (se informado) */}
                    {prize && (
                      <div className={`flex items-center gap-2 px-3 py-1 ${accentBgSoft} border-2 border-[hsl(var(--accent)/0.45)] rounded-lg`}>
                        <Trophy className={`w-4 h-4 ${accentText}`} />
                        <span className={`text-sm font-bold ${accentText}`}>Prêmio: {prize}</span>
                      </div>
                    )}
                  </div>
                  <p className={`text-sm ${primaryTextMuted}`}>Aguardando jogadores...</p>
                </div>
              </div>
              <Button
                onClick={handleStartQuiz}
                disabled={!leaderboard || leaderboard.players.length === 0}
                size="lg"
                className={`gap-2 ${buttonPrimary} ${buttonPrimaryHover} border border-[hsl(var(--border)/0.3)] card-glow`}
              >
                <Play className="w-5 h-5" />
                Iniciar Quiz
              </Button>
            </div>
          </Card>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {/* QR Code */}
            <Card className={`p-8 flex flex-col items-center justify-center ${accentBorder} card-glow`}>
              <h2 className={`text-2xl font-bold mb-4 ${accentText}`}>Escaneie para Jogar</h2>
              {session.qrCodeUrl && (
                <img
                  src={session.qrCodeUrl}
                  alt="QR Code"
                  className="w-full max-w-sm rounded-lg shadow-lg border-2 border-[hsl(var(--accent)/0.3)]"
                />
              )}
              <p className={`text-xl font-mono mt-4 ${accentText} tracking-wider`}>{session.sessionCode}</p>
            </Card>

            {/* Lista de jogadores */}
            <Card className={`p-8 ${accentBorder}`}>
              <div className="flex items-center gap-2 mb-6">
                <Users className={`w-6 h-6 ${accentText}`} />
                <h2 className={`text-2xl font-bold ${accentText}`}>
                  Jogadores ({leaderboard?.players.length || 0})
                </h2>
              </div>
              <div className="space-y-2 max-h-96 overflow-y-auto">
                {leaderboard?.players.map((player, index) => (
                  <div
                    key={player.id}
                    className={`flex items-center justify-between p-4 ${secondaryBg} rounded-lg ${accentBorderSoft}`}
                  >
                    <span className={`font-semibold ${primaryText}`}>
                      {index + 1}. {player.nickname}
                    </span>
                  </div>
                ))}
                {(!leaderboard || leaderboard.players.length === 0) && (
                  <p className={`text-center ${primaryTextSoft} py-8`}>
                    Aguardando jogadores...
                  </p>
                )}
              </div>
            </Card>
          </div>
        </div>
      </div>
    );
  }

  // Tela ACTIVE: mostrar pergunta e ranking
  if (session.status === 'ACTIVE' && currentQuestion) {
    return (
      <div className={`min-h-screen ${gradientBackground} p-8`}>
        <div className="max-w-7xl mx-auto space-y-8">
          {/* Header */}
          <Card className={`p-6 ${accentBorder}`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <img src={estabelecimentoImg} alt="Quiz Arena" className="h-16 w-16 object-cover rounded-lg border border-[hsl(var(--accent)/0.3)]" />
                <div>
                  <h1 className={`text-2xl font-bold ${accentText}`}>
                    Pergunta {currentQuestion.questionNumber} de {currentQuestion.totalQuestions}
                  </h1>
                  <p className={`text-sm ${primaryTextMuted}`}>
                    Tempo: {timeLeft !== null ? `${timeLeft}s` : `${currentQuestion.timeLimit}s`}
                  </p>
                </div>
              </div>
            <Button onClick={handleNextQuestion} size="lg" className={`gap-2 ${buttonPrimary} ${buttonPrimaryHover} border border-[hsl(var(--border)/0.3)]`}>
                <SkipForward className="w-5 h-5" />
                Próxima Pergunta
              </Button>
            </div>
          </Card>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {/* Pergunta */}
            <Card className={`md:col-span-2 p-8 ${accentBorder}`}>
              <h2 className={`text-3xl font-bold mb-8 text-center ${accentText}`}>
                {currentQuestion.question}
              </h2>
              <div className="grid grid-cols-2 gap-4">
                {currentQuestion.options.map((option, index) => (
                  <div
                    key={index}
                    className={`p-6 ${secondaryBg} rounded-lg text-center text-xl font-semibold ${primaryText} border border-[hsl(var(--accent)/0.15)]`}
                  >
                    {option}
                  </div>
                ))}
              </div>
            </Card>

            {/* Ranking */}
            <Card className={`p-6 ${accentBorder}`}>
              <h3 className={`text-xl font-bold mb-4 flex items-center gap-2 ${accentText}`}>
                <Trophy className={`w-5 h-5 ${accentText}`} />
                Ranking
              </h3>
              <div className="space-y-2">
                {leaderboard?.players.slice(0, 5).map((player) => {
                  const isHighlighted = highlightedPlayer === player.nickname;
                  return (
                    <div
                      key={player.id}
                      className={`flex items-center justify-between p-3 rounded-lg transition-all duration-500 ${
                        isHighlighted
                          ? `${accentBgSoft} border-2 border-[hsl(var(--accent)/0.6)] scale-105 animate-pulse card-glow`
                          : `${secondaryBg} ${accentBorderSoft}`
                      }`}
                    >
                      <span className={`font-semibold ${primaryText}`}>
                        {player.rank}. {player.nickname}
                        {isHighlighted && ' 🏆'}
                      </span>
                      <span className={`font-bold ${isHighlighted ? accentText : primaryText}`}>
                        {player.score}
                      </span>
                    </div>
                  );
                })}
              </div>
            </Card>
          </div>
        </div>
      </div>
    );
  }

  // Tela FINISHED: mostrar ranking final
  if (session.status === 'FINISHED' && leaderboard) {
    return (
      <div className={`min-h-screen ${gradientBackground} flex items-center justify-center p-8`}>
        <Card className={`p-12 max-w-4xl w-full relative overflow-hidden ${accentBorder} card-glow`}>
          <div className="absolute top-0 left-0 right-0 h-48">
            <img
              src={estabelecimentoImg}
              alt="Quiz Arena"
              className="w-full h-full object-cover object-center"
              style={{
                maskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)',
                WebkitMaskImage: 'linear-gradient(to bottom, rgba(0,0,0,1) 0%, rgba(0,0,0,0.7) 75%, rgba(0,0,0,0) 100%)'
              }}
            />
          </div>
          <div className="text-center mb-8 space-y-4 relative z-10 pt-32">
            <Trophy className={`w-20 h-20 mx-auto ${accentText} card-glow`} />
            <div>
              <h1 className={`text-5xl font-bold mb-4 ${accentText}`}>Quiz Finalizado!</h1>
              <p className={`text-xl ${primaryTextMuted}`}>Ranking Final</p>
            </div>
          </div>

          <div className="space-y-3">
            {leaderboard.players.map((player, index) => (
              <div
                key={player.id}
                className={`flex items-center justify-between p-6 rounded-lg ${
                  index === 0
                    ? `bg-[hsl(var(--accent)/0.25)] border-2 border-[hsl(var(--accent)/0.55)] card-glow`
                    : index === 1
                    ? `bg-[hsl(var(--secondary)/0.35)] border-2 border-[hsl(var(--border)/0.4)]`
                    : index === 2
                    ? `bg-[hsl(var(--card)/0.35)] border-2 border-[hsl(var(--border)/0.25)]`
                    : `${secondaryBg} ${accentBorderSoft}`
                }`}
              >
                <div className="flex items-center gap-4">
                  <span className={`text-3xl font-bold ${index === 0 ? accentText : primaryText}`}>
                    {index + 1}°
                  </span>
                  <div>
                    <p className={`text-2xl font-bold ${index === 0 ? accentText : primaryText}`}>
                      {player.nickname}
                    </p>
                    <p className={`text-sm ${primaryTextMuted}`}>
                      {player.correctAnswers} acertos
                    </p>
                  </div>
                </div>
                <span className={`text-3xl font-bold ${index === 0 ? accentText : primaryText}`}>
                  {player.score} pts
                </span>
              </div>
            ))}
          </div>

          <div className="mt-8 text-center">
            <Button
              onClick={() => window.location.reload()}
              size="lg"
              className={`px-6 py-3 ${buttonPrimary} ${buttonPrimaryHover} border border-[hsl(var(--border)/0.3)] card-glow`}
            >
              Nova Sessão
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return null;
}
