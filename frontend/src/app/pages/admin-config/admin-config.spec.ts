import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminConfigComponent } from './admin-config';
import { ConfigService } from '../../services/config.service';
import { ToastService } from '../../services/toast.service';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { ConfigCategory } from '../../models/config.model';
import { of } from 'rxjs';

describe('AdminConfigComponent', () => {
  let component: AdminConfigComponent;
  let fixture: ComponentFixture<AdminConfigComponent>;
  let httpMock: HttpTestingController;
  let configService: jasmine.SpyObj<ConfigService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let appConfigInitializer: jasmine.SpyObj<AppConfigInitializerService>;

  const mockCategories = [
    {
      category: 'AI',
      categoryLabel: 'Inteligencia Artificial',
      entries: [
        {
          key: 'AI_ENABLED',
          value: 'true',
          type: 'BOOLEAN' as const,
          description: 'Habilitar análisis con IA.',
          category: 'AI',
          categoryLabel: 'Inteligencia Artificial',
          minValue: null,
          maxValue: null,
          publiclyVisible: true,
          updatedAt: '2026-07-01T10:00:00',
          updatedBy: 'admin@test.com',
        },
        {
          key: 'AI_MODEL',
          value: 'qwen2.5:7b',
          type: 'STRING' as const,
          description: 'Modelo Ollama a utilizar.',
          category: 'AI',
          categoryLabel: 'Inteligencia Artificial',
          minValue: null,
          maxValue: null,
          publiclyVisible: false,
          updatedAt: null,
          updatedBy: null,
        },
      ],
    },
    {
      category: 'HEURISTIC',
      categoryLabel: 'Análisis Heurístico',
      entries: [
        {
          key: 'HEURISTIC_RISK_THRESHOLD_LOW',
          value: '40',
          type: 'INT' as const,
          description: 'Umbral de riesgo bajo.',
          category: 'HEURISTIC',
          categoryLabel: 'Análisis Heurístico',
          minValue: 0,
          maxValue: 100,
          publiclyVisible: true,
          updatedAt: null,
          updatedBy: null,
        },
        {
          key: 'HEURISTIC_RISK_THRESHOLD_MEDIUM',
          value: '70',
          type: 'INT' as const,
          description: 'Umbral de riesgo medio.',
          category: 'HEURISTIC',
          categoryLabel: 'Análisis Heurístico',
          minValue: 0,
          maxValue: 100,
          publiclyVisible: true,
          updatedAt: null,
          updatedBy: null,
        },
      ],
    },
    {
      category: 'RETENTION',
      categoryLabel: 'Retención de Datos',
      entries: [
        {
          key: 'AUDIT_RETENTION_DAYS',
          value: '365',
          type: 'INT' as const,
          description: 'Días de retención del log de auditoría.',
          category: 'RETENTION',
          categoryLabel: 'Retención de Datos',
          minValue: 1,
          maxValue: 3650,
          publiclyVisible: false,
          updatedAt: '2026-06-15T08:00:00',
          updatedBy: 'admin@test.com',
        },
      ],
    },
  ];

  beforeEach(async () => {
    configService = jasmine.createSpyObj<ConfigService>('ConfigService', [
      'getAllAdmin',
      'updateEntry',
      'purgeNow',
    ]);
    configService.getAllAdmin.and.returnValue(of(mockCategories));
    configService.updateEntry.and.returnValue(of({} as any));
    configService.purgeNow.and.returnValue(of({ purgedCount: 0, executedAt: '', durationMs: 0 } as any));

    toastService = jasmine.createSpyObj<ToastService>('ToastService', [
      'error',
      'success',
    ]);

    appConfigInitializer = jasmine.createSpyObj<AppConfigInitializerService>('AppConfigInitializerService', ['load']);

    await TestBed.configureTestingModule({
      imports: [AdminConfigComponent, HttpClientTestingModule],
      providers: [
        { provide: ConfigService, useValue: configService },
        { provide: ToastService, useValue: toastService },
        { provide: AppConfigInitializerService, useValue: appConfigInitializer },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminConfigComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads categories from API', () => {
    expect(configService.getAllAdmin).toHaveBeenCalled();
    expect(component.categories().length).toBe(3);
    expect(component.categories()[0].entries.length).toBe(2);
  });

  it('starts edit mode on entry click', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);

    expect(component.editingKey()).toBe('AI_ENABLED');
    expect(component.editingValue()).toBe('true');
  });

  it('cancels edit mode', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);
    component.onCancel();

    expect(component.editingKey()).toBeNull();
    expect(component.editingValue()).toBe('');
  });

  it('saves updated boolean value via API', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);
    component.editingValue.set('false');
    component.onSave('AI_ENABLED');

    expect(configService.updateEntry).toHaveBeenCalledWith('AI_ENABLED', 'false');
    expect(component.editingKey()).toBeNull();
  });

  it('calls appConfigInitializer.load() after successful update', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);
    component.editingValue.set('false');
    component.onSave('AI_ENABLED');

    expect(appConfigInitializer.load).toHaveBeenCalled();
  });

  it('rejects non-numeric input for INT type', () => {
    const entry = component.categories()[1].entries[0];
    component.onEdit(entry);
    component.editingValue.set('not-a-number');
    component.onSave('HEURISTIC_RISK_THRESHOLD_LOW');

    expect(configService.updateEntry).not.toHaveBeenCalled();
    expect(component.editingKey()).toBe('HEURISTIC_RISK_THRESHOLD_LOW');
  });

  it('canSave returns false when value unchanged', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);
    expect(component.canSave('AI_ENABLED')).toBeFalse();
  });

  it('canSave returns true for valid edited value', () => {
    const entry = component.categories()[0].entries[0];
    component.onEdit(entry);
    component.editingValue.set('false');
    expect(component.canSave('AI_ENABLED')).toBeTrue();
  });

  it('requests purge confirmation for AUDIT_RETENTION_DAYS', () => {
    component.onPurgeRequest('AUDIT_RETENTION_DAYS');
    expect(component.purgeConfirmKey()).toBe('AUDIT_RETENTION_DAYS');
  });

  it('cancels purge confirmation', () => {
    component.onPurgeRequest('AUDIT_RETENTION_DAYS');
    component.onPurgeCancel();
    expect(component.purgeConfirmKey()).toBeNull();
  });

  it('executes purge via API', () => {
    component.onPurgeRequest('AUDIT_RETENTION_DAYS');
    component.onPurgeConfirm('AUDIT_RETENTION_DAYS');

    expect(configService.purgeNow).toHaveBeenCalledWith('AUDIT_RETENTION_DAYS');
    expect(component.purging()).toBeNull();
    expect(component.purgeConfirmKey()).toBeNull();
  });

  it('formats BOOLEAN values as Spanish labels', () => {
    const entry = component.categories()[0].entries[0];
    expect(component.formatValue(entry)).toBe('Activado');
  });

  it('formats INT values as-is', () => {
    const entry = component.categories()[1].entries[0];
    expect(component.formatValue(entry)).toBe('40');
  });

  it('formats STRING values as-is', () => {
    const entry = component.categories()[0].entries[1];
    expect(component.formatValue(entry)).toBe('qwen2.5:7b');
  });

  it('returns category icon mapping', () => {
    expect(component.categoryIcon({ category: 'AI', categoryLabel: '', entries: [] })).toBe('ti ti-brain');
    expect(component.categoryIcon({ category: 'HEURISTIC', categoryLabel: '', entries: [] })).toBe('ti ti-shield-check');
    expect(component.categoryIcon({ category: 'RETENTION', categoryLabel: '', entries: [] })).toBe('ti ti-eraser');
    expect(component.categoryIcon({ category: 'UNKNOWN', categoryLabel: '', entries: [] })).toBe('ti ti-settings');
  });

  it('returns Spanish label for known keys', () => {
    expect(component.labelFor('AI_ENABLED')).toBe('IA activada');
    expect(component.labelFor('AUDIT_RETENTION_DAYS')).toBe('Retención de auditoría (días)');
    expect(component.labelFor('HEURISTIC_RISK_THRESHOLD_LOW')).toBe('Umbral de riesgo bajo');
  });

  it('returns key as fallback for unknown keys', () => {
    expect(component.labelFor('UNKNOWN_KEY')).toBe('UNKNOWN_KEY');
  });

  it('returns relative time for dates', () => {
    const past = new Date(Date.now() - 3600_000 * 5).toISOString();
    expect(component.relativeTime(past)).toContain('hace');
  });

  it('returns "nunca" for null dates', () => {
    expect(component.relativeTime(null)).toBe('nunca');
  });

  it('isPurgable returns true for retention keys', () => {
    expect(component.isPurgable('AUDIT_RETENTION_DAYS')).toBeTrue();
    expect(component.isPurgable('EMAIL_RETENTION_DAYS')).toBeTrue();
    expect(component.isPurgable('AI_ENABLED')).toBeFalse();
  });

  it('filters out stale DB rows whose key is not in the current enum', () => {
    const catsWithStale = [
      {
        category: 'AI',
        categoryLabel: 'Inteligencia Artificial',
        entries: [
          {
            key: 'AI_ENABLED',
            value: 'true',
            type: 'BOOLEAN' as const,
            description: 'Habilitar análisis con IA.',
            category: 'AI',
            categoryLabel: 'Inteligencia Artificial',
            minValue: null,
            maxValue: null,
            publiclyVisible: true,
            updatedAt: null,
            updatedBy: null,
          },
          {
            key: 'TRIAL_AI_EXPLANATION_LIMIT',
            value: '10',
            type: 'INT' as const,
            description: 'Old stale key.',
            category: 'AI',
            categoryLabel: 'Inteligencia Artificial',
            minValue: null,
            maxValue: null,
            publiclyVisible: false,
            updatedAt: null,
            updatedBy: null,
          },
          {
            key: 'HEURISTIC_RISK_THRESHOLDS',
            value: '{"low":40,"medium":70}',
            type: 'STRING' as const,
            description: 'Another stale key.',
            category: 'AI',
            categoryLabel: 'Inteligencia Artificial',
            minValue: null,
            maxValue: null,
            publiclyVisible: false,
            updatedAt: null,
            updatedBy: null,
          },
        ],
      },
    ];
    const result = component['filterValidKeys'](catsWithStale as ConfigCategory[]);
    expect(result.length).toBe(1);
    expect(result[0].entries.length).toBe(1);
    expect(result[0].entries[0].key).toBe('AI_ENABLED');
  });

  it('formatValue returns "(vacío)" for empty STRING values', () => {
    const entry = {
      key: 'COPY_SUPPORT_EMAIL',
      value: '',
      type: 'STRING' as const,
      description: '',
      category: 'COPY',
      categoryLabel: '',
      minValue: null,
      maxValue: null,
      publiclyVisible: false,
      updatedAt: null,
      updatedBy: null,
    };
    expect(component.formatValue(entry)).toBe('(vacío)');
  });

  it('formatValue returns "(vacío)" for null STRING values', () => {
    const entry = {
      key: 'COPY_SUPPORT_EMAIL',
      value: null as any,
      type: 'STRING' as const,
      description: '',
      category: 'COPY',
      categoryLabel: '',
      minValue: null,
      maxValue: null,
      publiclyVisible: false,
      updatedAt: null,
      updatedBy: null,
    };
    expect(component.formatValue(entry)).toBe('(vacío)');
  });

  it('formatValue truncates very long STRING values', () => {
    const longString = 'a'.repeat(100);
    const entry = {
      key: 'OAUTH_ALLOWED_DOMAINS',
      value: longString,
      type: 'STRING' as const,
      description: '',
      category: 'SECURITY',
      categoryLabel: '',
      minValue: null,
      maxValue: null,
      publiclyVisible: false,
      updatedAt: null,
      updatedBy: null,
    };
    const result = component.formatValue(entry);
    expect(result.endsWith('…')).toBeTrue();
    expect(result.length).toBe(61);
    expect(result.startsWith('aaaa')).toBeTrue();
  });

  it('isValueEmpty returns true for empty STRING entries', () => {
    const entry = {
      key: 'COPY_SUPPORT_EMAIL',
      value: '   ',
      type: 'STRING' as const,
      description: '',
      category: 'COPY',
      categoryLabel: '',
      minValue: null,
      maxValue: null,
      publiclyVisible: false,
      updatedAt: null,
      updatedBy: null,
    };
    expect(component.isValueEmpty(entry)).toBeTrue();
  });

  it('isValueEmpty returns false for BOOLEAN entries', () => {
    const entry = {
      key: 'AI_ENABLED',
      value: '',
      type: 'BOOLEAN' as const,
      description: '',
      category: 'AI',
      categoryLabel: '',
      minValue: null,
      maxValue: null,
      publiclyVisible: false,
      updatedAt: null,
      updatedBy: null,
    };
    expect(component.isValueEmpty(entry)).toBeFalse();
  });
});
