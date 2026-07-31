export interface QuizSession {
  id: number;
  sessionCode: string;
  status: 'WAITING' | 'ACTIVE' | 'PAUSED' | 'FINISHED';
  createdAt: string;
  currentQuestionIndex: number;
  totalQuestions: number;
  questionTimeLimit: number;
  autoAdvance?: boolean;
  qrCodeUrl?: string;
}

export interface Player {
  id: number;
  nickname: string;
  score: number;
  correctAnswers: number;
  wrongAnswers: number;
  rank?: number;
}

export interface Question {
  questionId: number;
  question: string;
  options: string[];
  questionNumber: number;
  totalQuestions: number;
  timeLimit: number;
  imageUrl?: string;
}

export interface AnswerResult {
  correct: boolean;
  correctOption: number;
  pointsEarned: number;
  totalScore: number;
  rank: number;
}

export interface Leaderboard {
  players: Player[];
  currentQuestion: number;
  totalQuestions: number;
}

export interface PlayerJoinRequest {
  nickname: string;
  sessionCode: string;
}

export interface AnswerSubmitRequest {
  questionId: number;
  selectedOption: number;
  responseTimeMs: number;
}

export interface SessionCreateRequest {
  numberOfQuestions: number;
  questionTimeLimit: number;
  category?: string;
  autoAdvance?: boolean;
}

export interface Category {
  id: number;
  name: string;
  description: string;
  difficultyLevel: 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';
  icon: string;
  color: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  questionCount: number;
}
