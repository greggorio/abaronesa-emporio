import React from 'react';
import { Edit, Copy, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface Question {
  id: number;
  question: string;
  category?: {
    id: number;
    name: string;
  };
  points: number;
  active: boolean;
  options?: string[];
  correctAnswer?: number;
}

interface QuestionItemProps {
  question: Question;
  isSelected: boolean;
  onSelect: () => void;
  onEdit: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}

const QuestionItem: React.FC<QuestionItemProps> = ({
  question,
  isSelected,
  onSelect,
  onEdit,
  onDuplicate,
  onDelete
}) => {
  return (
    <div
      className={`rounded-lg p-4 cursor-pointer hover:bg-white transition-colors shadow-sm ${
        isSelected ? 'bg-white ring-2 ring-[#D7B899]' : 'bg-white'
      }`}
      onClick={onSelect}
    >
      <div className="flex justify-between">
        <div>
          <h3 className="font-medium text-[#2A1F1B] line-clamp-2">{question.question}</h3>
          <div className="flex gap-2 mt-2">
            <Badge variant="secondary" className="bg-[#D7B899]/20 text-[#8B7355]">
              {question.category?.name || 'Sem categoria'}
            </Badge>
            <Badge variant="secondary" className="bg-[#8B7355]/10 text-[#8B7355]">
              {question.points} pts
            </Badge>
            <Badge variant={question.active ? "default" : "outline"} className={question.active ? "bg-green-500" : "bg-gray-300"}>
              {question.active ? 'Ativa' : 'Inativa'}
            </Badge>
          </div>
        </div>

        <div className="flex gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#6B3E26]"
            onClick={(e) => {
              e.stopPropagation();
              onEdit();
            }}
          >
            <Edit className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#6B3E26]"
            onClick={(e) => {
              e.stopPropagation();
              onDuplicate();
            }}
          >
            <Copy className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 text-[#8B7355] hover:text-[#D65A31]"
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </div>
  );
};

export default QuestionItem;