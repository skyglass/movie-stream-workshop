import { UserConfigFn } from 'vite';
import { overrideVaadinConfig } from './vite.generated';

const customConfig: UserConfigFn = () => ({
});

export default overrideVaadinConfig(customConfig);
