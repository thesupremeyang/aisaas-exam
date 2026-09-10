export interface SubmissionForm {
  applicant: {
    name: string
    identityNumber: string
    phone: string
    organization: string
  }
  work: {
    title: string
    scene: string
    functionDescription: string
    innovationDescription: string
    promotionDescription: string
    dataDescription: string
    runtimeEnvironment: string
    teamDescription: string
    technicalDescription: string
    hasSkill: boolean
    hasMcp: boolean
    hasKnowledge: boolean
    hasDemo: boolean
  }
  materials: {
    demoUrl: string
    videoFiles: MaterialFile[]
    screenshotFiles: MaterialFile[]
    packageFiles: MaterialFile[]
    supplementaryFiles: MaterialFile[]
  }
  declarationAccepted: boolean
}

export interface MaterialFile {
  id: string
  name: string
  size: number
}

export function emptySubmissionForm(): SubmissionForm {
  return {
    applicant: { name: '', identityNumber: '', phone: '', organization: '' },
    work: {
      title: '',
      scene: '',
      functionDescription: '',
      innovationDescription: '',
      promotionDescription: '',
      dataDescription: '',
      runtimeEnvironment: '',
      teamDescription: '',
      technicalDescription: '',
      hasSkill: false,
      hasMcp: false,
      hasKnowledge: false,
      hasDemo: false,
    },
    materials: {
      demoUrl: '',
      videoFiles: [],
      screenshotFiles: [],
      packageFiles: [],
      supplementaryFiles: [],
    },
    declarationAccepted: false,
  }
}

export function decodeSubmissionForm(value?: string | null): SubmissionForm {
  if (!value) return emptySubmissionForm()
  try {
    const parsed = JSON.parse(value) as Partial<SubmissionForm>
    const empty = emptySubmissionForm()
    return {
      applicant: { ...empty.applicant, ...parsed.applicant },
      work: { ...empty.work, ...parsed.work },
      materials: { ...empty.materials, ...parsed.materials },
      declarationAccepted: Boolean(parsed.declarationAccepted),
    }
  } catch {
    return emptySubmissionForm()
  }
}

export function encodeSubmissionForm(form: SubmissionForm): string {
  return JSON.stringify(form)
}
