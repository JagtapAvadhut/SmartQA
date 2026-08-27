import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'

export function ClarificationModal({
  title = 'SmartQA needs a choice',
  question,
  options,
  onSelect,
}: {
  title?: string
  question: string
  options: Array<{ id: string; label: string }>
  onSelect: (id: string) => void
}) {
  return (
    <Modal title={title}>
      <p className="mb-4 text-sm text-muted">{question}</p>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <Button key={option.id} variant="primary" type="button" onClick={() => onSelect(option.id)}>
            {option.label}
          </Button>
        ))}
      </div>
    </Modal>
  )
}
