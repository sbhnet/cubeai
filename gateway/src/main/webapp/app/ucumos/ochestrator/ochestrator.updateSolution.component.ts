import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import {SnackBarService} from '../../shared';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {CompositeSolutionService} from '../service/compositeSolution.service';

@Component({
    templateUrl: './ochestrator.updateSolution.component.html'
})
export class UpdateSolutionComponent implements OnInit {
    formGroup: FormGroup;
    sol: any;
    get solution() { return this.formGroup.get('solution'); }
    get version() { return this.formGroup.get('version'); }
    get description() { return this.formGroup.get('description'); }

    constructor(
        private formBuilder: FormBuilder,
        public dialogRef: MatDialogRef<UpdateSolutionComponent>,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private snackBarService: SnackBarService,
        private compositeSolutionService: CompositeSolutionService
    ) {
        this.formGroup = this.formBuilder.group({
            solution: ['', {
                validators: [Validators.required, Validators.maxLength(50),
                    Validators.pattern('^[_.@A-Za-z0-9-]*$')],
                updateOn: 'blur',
            }],
            version: ['', {
                validators: [ Validators.maxLength(50),
                    Validators.pattern('^[_.@A-Za-z0-9-]*$')],
                updateOn: 'blur',
            }],
            description: ['', {
                validators: [Validators.maxLength(50),
                    Validators.pattern('^[_.@A-Za-z0-9-]*$')],
                updateOn: 'blur',
            }],
         });
    }

    ngOnInit() {
        this.sol = {};
        this.formGroup.get('solution').setValue('......');
    }

    onClose(): void {
        this.dialogRef.close(null);
    }

    onSubmit() {
        this.sol.uuid = this.data.uuid;
        this.sol.name = this.solution.value;
        this.sol.authorLogin = this.data.fromUserLogin;
        this.sol.version = this.version.value;
        this.sol.summary = this.description.value;

        // 向后端发送组合解决方案update请求
        this.compositeSolutionService.updateCompositeSolution(this.sol).subscribe((res) => {
            console.log(res);
            this.dialogRef.close(this.sol);
        });
    }

}
