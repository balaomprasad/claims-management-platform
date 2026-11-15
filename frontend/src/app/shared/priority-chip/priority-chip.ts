import { Component, computed, input } from '@angular/core';
import { ClaimPriority } from '../../core/models/claim.model';
import { humanize } from '../../core/models/reference.model';

@Component({
  selector: 'app-priority-chip',
  template: `<span class="priority" [class]="'priority ' + cssClass()">{{ label() }}</span>`,
  styles: `
    .priority {
      font-size: 12px;
      font-weight: 500;
      padding: 1px 8px;
      border-radius: 4px;
      border: 1px solid currentColor;
      white-space: nowrap;
    }
    .low { color: #546e7a; }
    .normal { color: #1565c0; }
    .high { color: #e65100; }
    .urgent { color: #fff; background: #c62828; border-color: #c62828; }
  `,
})
export class PriorityChip {
  readonly priority = input.required<ClaimPriority>();
  protected readonly label = computed(() => humanize(this.priority()));
  protected readonly cssClass = computed(() => this.priority().toLowerCase());
}
