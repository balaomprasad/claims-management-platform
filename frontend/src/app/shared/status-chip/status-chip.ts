import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../../core/models/claim.model';

@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="'chip ' + cssClass()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
    }
    .submitted { background: #e3f2fd; color: #0d47a1; }
    .under_review { background: #fff8e1; color: #8d6e00; }
    .approved { background: #e8f5e9; color: #1b5e20; }
    .rejected { background: #ffebee; color: #b71c1c; }
    .settled { background: #ede7f6; color: #4527a0; }
    .withdrawn { background: #eceff1; color: #455a64; }
  `,
})
export class StatusChip {
  readonly status = input.required<ClaimStatus>();
  protected readonly label = computed(() => this.status().replace('_', ' '));
  protected readonly cssClass = computed(() => this.status().toLowerCase());
}
