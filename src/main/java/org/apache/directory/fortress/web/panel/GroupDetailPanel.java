/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.fortress.web.panel;

import com.googlecode.wicket.jquery.core.Options;
import com.googlecode.wicket.kendo.ui.datatable.ColumnButton;
import com.googlecode.wicket.kendo.ui.datatable.DataTable;
import com.googlecode.wicket.kendo.ui.datatable.column.CommandsColumn;
import com.googlecode.wicket.kendo.ui.datatable.column.IColumn;
import com.googlecode.wicket.kendo.ui.datatable.column.PropertyColumn;
import com.googlecode.wicket.kendo.ui.form.combobox.ComboBox;
import org.apache.log4j.Logger;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.directory.fortress.web.GlobalIds;
import org.apache.directory.fortress.web.GlobalUtils;
import org.apache.directory.fortress.web.SaveModelEvent;
import org.apache.directory.fortress.web.SecureIndicatingAjaxButton;
import org.apache.directory.fortress.web.SelectModelEvent;
import org.apache.directory.fortress.core.ldap.group.Group;
import org.apache.directory.fortress.core.ldap.group.GroupMgr;
import org.apache.directory.fortress.core.rbac.User;
import org.apache.directory.fortress.core.util.attr.VUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @author Shawn McKinney
 * @version $Rev$
 */
public class GroupDetailPanel extends FormComponentPanel
{
    @SpringBean
    private GroupMgr groupMgr;
    private static final Logger log = Logger.getLogger( GroupDetailPanel.class.getName() );
    private Form editForm;
    private Displayable display;
    public static final int ROWS = 5;

    public Form getForm()
    {
        return this.editForm;
    }

    public GroupDetailPanel( String id, Displayable display )
    {
        super( id );

        this.groupMgr.setAdmin( GlobalUtils.getRbacSession( this ) );
        this.editForm = new GroupDetailForm( GlobalIds.EDIT_FIELDS, new CompoundPropertyModel<Group>( new Group() ) );
        editForm.setOutputMarkupId( true );
        this.display = display;
        add( editForm );
    }

    public class GroupDetailForm extends Form
    {
        private Component component;
        private DataTable<Member> table;
        private List<IColumn> columns;
        private Options options;
        private String memberAssign;
        private TextField memberAssignTF;
        private ComboBox<String> memberPropsCB;
        private String memberPropsSelection;

        public GroupDetailForm( String id, final IModel<Group> model )
        {
            super( id, model );
            addGroupDetailFields();
            addGroupButtons();
            setOutputMarkupId( true );
        }

        private void addGroupDetailFields()
        {
            add( new Label( "groupAssignmentsLabel", "Group Detail" ) );
            TextField name = new TextField( "name" );
            add( name );
            name.setRequired( false );
            TextField protocol = new TextField( "protocol" );
            add( protocol );
            TextField description = new TextField( "description" );
            description.setRequired( false );
            add( description );
            protocol.setRequired( true );
            memberPropsCB = new ComboBox<String>( "memberProps", new PropertyModel<String>( this, "memberPropsSelection" ), new ArrayList<String>() );
            memberPropsCB.setOutputMarkupId( true );
            add( memberPropsCB );

            memberAssignTF = new TextField( "memberAssign", new PropertyModel( this, "memberAssign" ) );
            memberAssignTF.setOutputMarkupId( true );
            add( memberAssignTF );
            addUserSearchModal();

            // DataTable //
            columns = newColumnList();
            options = new Options();
            options.set("selectable", Options.asString("single"));
            options.set("scrollable", "{ virtual: true }"); //infinite scroll
            options.set( "height", 300 );
            options.set( "pageable", "{ pageSizes: [ 5, 10, 15, 20 ] }" );
            table = new DataTable<Member>( "memberstable", columns, createDataProvider( null ), ROWS, options );
            table.setOutputMarkupId( true );
            add( table );
        }

        private void addGroupButtons()
        {
            add( new SecureIndicatingAjaxButton( GlobalIds.ADD, GlobalIds.GROUP_MGR, "add" )
            {
                @Override
                protected void onSubmit( AjaxRequestTarget target, Form form )
                {
                    log.debug( ".onSubmit Add" );
                    Group group = ( Group ) form.getModel().getObject();
                    String msg = null;
                    if(!VUtil.isNotNullOrEmpty( memberAssign ) && !VUtil.isNotNullOrEmpty( group.getMembers() ))
                    {
                        msg = "Group name: " + group.getName() + " cannot be added without a member";
                    }
                    else
                    {
                        try
                        {
                            if(VUtil.isNotNullOrEmpty( memberAssign ))
                            {
                                group.setMember( memberAssign );
                            }
                            group.setMemberDn( true );
                            group = groupMgr.add( group );
                            component = editForm;
                            SaveModelEvent.send( getPage(), this, group, target, SaveModelEvent.Operations.ADD );
                            msg = "Group name: " + group.getName() + " has been added";
                        }
                        catch ( org.apache.directory.fortress.core.SecurityException se )
                        {
                            String error = ".onSubmit caught SecurityException=" + se;
                            log.error( error );
                            display.setMessage( error );
                        }
                    }
                    display.setMessage( msg );
                }

                @Override
                public void onError( AjaxRequestTarget target, Form form )
                {
                    log.info( "GroupDetailPanel.add.onError caught" );
                    target.add();
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );
            add( new SecureIndicatingAjaxButton( GlobalIds.COMMIT, GlobalIds.GROUP_MGR, "update" )
            {
                @Override
                protected void onSubmit( AjaxRequestTarget target, Form form )
                {
                    log.debug( ".onSubmit Commit" );
                    Group group = ( Group ) form.getModel().getObject();
                    try
                    {
                        group = groupMgr.update( group );
                        String msg = "Group name: " + group.getName() + " has been updated";
                        SaveModelEvent.send( getPage(), this, group, target, SaveModelEvent.Operations.UPDATE );
                        component = editForm;
                        display.setMessage( msg );
                    }
                    catch ( org.apache.directory.fortress.core.SecurityException se )
                    {
                        String error = ".onSubmit caught SecurityException=" + se;
                        log.error( error );
                        display.setMessage( error );
                    }
                }

                @Override
                public void onError( AjaxRequestTarget target, Form form )
                {
                    log.warn( "GroupDetailPanel.commit.onError" );
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );
            add( new SecureIndicatingAjaxButton( GlobalIds.DELETE, GlobalIds.GROUP_MGR, "delete" )
            {
                @Override
                protected void onSubmit( AjaxRequestTarget target, Form form )
                {
                    log.debug( ".onSubmit Commit" );
                    Group group = ( Group ) form.getModel().getObject();
                    try
                    {
                        groupMgr.delete( group );
                        clearDetailFields( "Group name: " + group.getName() + " has been deleted", target, form );
                        SaveModelEvent.send( getPage(), this, group, target, SaveModelEvent.Operations.DELETE );
                    }
                    catch ( org.apache.directory.fortress.core.SecurityException se )
                    {
                        String error = ".onSubmit caught SecurityException=" + se;
                        log.error( error );
                        display.setMessage( error );
                    }
                }

                @Override
                public void onError( AjaxRequestTarget target, Form form )
                {
                    log.warn( "GroupDetailPanel.delete.onError" );
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );
            add( new AjaxSubmitLink( GlobalIds.CANCEL )
            {
                @Override
                protected void onSubmit( AjaxRequestTarget target, Form form )
                {
                    clearDetailFields( "Group cancelled input form", target, form );
                }

                @Override
                public void onError( AjaxRequestTarget target, Form form )
                {
                    log.warn( "GroupDetailPanel.cancel.onError" );
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );

            add( new SecureIndicatingAjaxButton( "memberProps.add", GlobalIds.GROUP_MGR, "addProperty" )
            {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit( AjaxRequestTarget target, Form<?> form )
                {
                    String msg = "clicked on memberProps.add";
                    if ( VUtil.isNotNullOrEmpty( memberPropsSelection ) )
                    {
                        msg += " selection:" + memberPropsSelection;
                        Group group = ( Group ) form.getModel().getObject();
                        int idx = memberPropsSelection.indexOf( '=' );
                        if ( idx != -1 )
                        {
                            String key = memberPropsSelection.substring( 0, idx );
                            String val = memberPropsSelection.substring( idx  + 1);
                            try
                            {
                                Group newGroup = groupMgr.add( group, key, val );
                                group.setProperties( newGroup.getProperties() );
                                memberPropsCB = new ComboBox<String>( "memberProps", new PropertyModel<String>( form, "memberPropsSelection" ), group.getPropList() );
                                form.addOrReplace( memberPropsCB );
                            }
                            catch( org.apache.directory.fortress.core.SecurityException se)
                            {
                                String error = "Failed add property: " + memberPropsSelection + ", SecurityException=" + se;
                                log.warn( error );
                                display.setMessage( error );
                            }
                        }
                        memberPropsSelection = "";
                        component = editForm;
                        msg += ", was added";
                    }
                    else
                    {
                        msg += ", no action taken because property selection is empty";
                    }
                    display.setMessage( msg );
                    log.debug( msg );
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );

            add( new SecureIndicatingAjaxButton( "memberProps.delete", GlobalIds.GROUP_MGR, "deleteProperty" )
            {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit( AjaxRequestTarget target, Form<?> form )
                {
                    String msg = "clicked on memberProps.delete";
                    if ( VUtil.isNotNullOrEmpty( memberPropsSelection ) )
                    {
                        msg += " selection:" + memberPropsSelection;
                        Group group = ( Group ) form.getModel().getObject();
                        if ( group.getProperties() != null )
                        {
                            int idx = memberPropsSelection.indexOf( '=' );
                            if ( idx != -1 )
                            {
                                String key = memberPropsSelection.substring( 0, idx );
                                String val = memberPropsSelection.substring( idx  + 1);
                                try
                                {
                                    Group newGroup = groupMgr.delete( group, key, val );
                                    group.setProperties( newGroup.getProperties() );
                                    memberPropsCB = new ComboBox<String>( "memberProps", new PropertyModel<String>( form, "memberPropsSelection" ), group.getPropList() );
                                    form.addOrReplace( memberPropsCB );
                                }
                                catch( org.apache.directory.fortress.core.SecurityException se)
                                {
                                    String error = "Failed delete property: " + memberPropsSelection + ", SecurityException=" + se;
                                    log.warn( error );
                                    display.setMessage( error );
                                }
                            }
                            memberPropsSelection = "";
                            component = editForm;
                            msg += ", was removed";
                        }
                        else
                        {
                            msg += ", no action taken because group does not have properties set";
                        }
                    }
                    else
                    {
                        msg += ", no action taken because property selection is empty";
                    }
                    display.setMessage( msg );
                    log.debug( msg );
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );

            add( new SecureIndicatingAjaxButton( "member.assign", GlobalIds.GROUP_MGR, "assign" )
            {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit( AjaxRequestTarget target, Form<?> form )
                {
                    Group group = ( Group ) form.getModel().getObject();
                    if( VUtil.isNotNullOrEmpty( memberAssign ) )
                    {
                        try
                        {
                            // TODO: figure out how to get the table to refresh its values here:
                            String userId = GlobalUtils.getRdn( memberAssign );
                            Group newGroup = groupMgr.assign( group, userId );
                            group.setMembers( newGroup.getMembers() );

                            String msg = "Group: " + group.getName() + ", member: " + memberAssign + ", has been assigned";
                            memberAssign = "";
                            form.add( memberAssignTF );
                            addMemberTable( group );
                            display.setMessage( msg );
                            log.debug( msg );
                        }
                        catch( org.apache.directory.fortress.core.SecurityException se)
                        {
                            String error = "Failed assign user: " + memberAssign + ", SecurityException=" + se;
                            log.warn( error );
                            display.setMessage( error );
                        }
                    }
                    else
                    {
                        String msg = "Group: " + group.getName() + ", assign op ignored, no value entered for assignment";
                        display.setMessage( msg );
                        log.debug( msg );
                    }
                    component = editForm;
                }
                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );
        }

        private void clearDetailFields( String msg, AjaxRequestTarget target, Form form )
        {
            setModelObject( new Group() );
            memberPropsCB = new ComboBox<String>( "memberProps", new PropertyModel<String>( form, "memberPropsSelection" ), new ArrayList<String>() );
            editForm.addOrReplace( memberPropsCB );
            table.refresh( target );
            table = new DataTable<Member>( "memberstable", columns, createDataProvider( null ), ROWS, options );
            editForm.addOrReplace( table );
            modelChanged();
            component = editForm;
            display.setMessage( msg );
        }

        private List<IColumn> newColumnList()
        {
            List<IColumn> columns = new ArrayList<IColumn>();
            columns.add( new PropertyColumn( "#", "index", 30 ) );
            columns.add( new PropertyColumn( "User DN", "userDn", 150 ) );
            columns.add(new CommandsColumn("", 100) {

                private static final long serialVersionUID = 1L;

                @Override
                public List<ColumnButton> newButtons()
                {
                    return Arrays.asList( new ColumnButton( "remove", "userDn" ) );
                }
            });

            return columns;
        }

        private IDataProvider<Member> createDataProvider( List<String> members )
        {
            ListDataProvider<Member> results;
            if ( VUtil.isNotNullOrEmpty( members ) )
            {
                int ctr = 0;
                List<Member> tableMembers = new ArrayList<Member>();
                for(String member : members)
                {
                    Member tableMember = new Member();
                    tableMember.setUserDn( member );
                    tableMember.setIndex( ++ctr );
                    tableMembers.add( tableMember );
                }
                results = new ListDataProvider<Member>( tableMembers );
            }
            else
            {
                results = new ListDataProvider<Member>( new ArrayList<Member>() );
            }
            return results;
        }

        private void addUserSearchModal()
        {
            final ModalWindow membersModalWindow;
            add( membersModalWindow = new ModalWindow( "membersmodal" ) );
            final UserSearchModalPanel memberSearchModalPanel = new UserSearchModalPanel( membersModalWindow.getContentId(), membersModalWindow );
            membersModalWindow.setContent( memberSearchModalPanel );
            membersModalWindow.setWindowClosedCallback( new ModalWindow.WindowClosedCallback()
            {
                @Override
                public void onClose( AjaxRequestTarget target )
                {
                    User user = memberSearchModalPanel.getUserSelection();
                    if ( user != null )
                    {
                        setMemberAssign( user.getDn() );
                        target.add( memberAssignTF );
                    }
                }
            } );

            add( new SecureIndicatingAjaxButton( "members.search", GlobalIds.REVIEW_MGR, "findUsers" )
            {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit( AjaxRequestTarget target, Form<?> form )
                {
                    String msg = "clicked on members search";
                    msg += memberAssign != null ? ": " + memberAssign : "";
                    display.setMessage( msg );
                    log.debug( msg );
                    if(VUtil.isNotNullOrEmpty( memberAssign ))
                    {
                        memberSearchModalPanel.setSearchVal(memberAssign);
                    }
                    target.prependJavaScript( GlobalIds.WICKET_WINDOW_UNLOAD_CONFIRMATION_FALSE );
                    membersModalWindow.show( target );
                }

                @Override
                protected void updateAjaxAttributes( AjaxRequestAttributes attributes )
                {
                    super.updateAjaxAttributes( attributes );
                    AjaxCallListener ajaxCallListener = new AjaxCallListener()
                    {
                        @Override
                        public CharSequence getFailureHandler( Component component )
                        {
                            return GlobalIds.WINDOW_LOCATION_REPLACE_COMMANDER_HOME_HTML;
                        }
                    };
                    attributes.getAjaxCallListeners().add( ajaxCallListener );
                }
            } );

            membersModalWindow.setTitle( "Members Selection Modal" );
            membersModalWindow.setInitialWidth( 450 );
            membersModalWindow.setInitialHeight( 450 );
            membersModalWindow.setCookieName( "members-modal" );
        }

        @Override
        public void onEvent( final IEvent<?> event )
        {
            if ( event.getPayload() instanceof SelectModelEvent )
            {
                SelectModelEvent modelEvent = ( SelectModelEvent ) event.getPayload();
                final Group group = ( Group ) modelEvent.getEntity();
                this.setModelObject(group);
                memberPropsSelection = "";
                if(VUtil.isNotNullOrEmpty(group.getProperties()))
                {
                    memberPropsCB = new ComboBox<String>( "memberProps", new PropertyModel<String>( this, "memberPropsSelection" ), group.getPropList() );
                    editForm.addOrReplace(memberPropsCB);
                }
                addMemberTable( group );
                String msg = "Group Name: " + group.getName() + " has been selected";
                display.setMessage( msg );
                log.debug( msg );
                component = editForm;
            }
            else if ( event.getPayload() instanceof AjaxRequestTarget )
            {
                if ( component != null )
                {
                     AjaxRequestTarget target = ( ( AjaxRequestTarget ) event.getPayload() );
                    log.debug( ".onEvent AjaxRequestTarget: " + target.toString() );
                    target.add( component );
                    component = null;
                }

                display.display( ( AjaxRequestTarget ) event.getPayload() );
            }
        }

        private void addMemberTable( final Group group )
        {
            table = new DataTable<Member>( "memberstable", columns, createDataProvider( group.getMembers() ), ROWS,
                options )
            {
                /**
                 * Triggered when a column button is clicked.
                 */
                @Override
                public void onClick(AjaxRequestTarget target, ColumnButton button, String value)
                {
                    if( VUtil.isNotNullOrEmpty( value ) )
                    {
                        try
                        {
                            // TODO: figure out how to get the table to refresh its values here:
                            String userId = GlobalUtils.getRdn( value );
                            Group newGroup = groupMgr.deassign( group, userId );
                            group.setMembers( newGroup.getMembers() );
                            table.refresh( target );
                            String msg = "User: " + userId + ", deassigned from group: " + group.getName();
                            display.setMessage( msg );
                            log.debug( msg );
                        }
                        catch( org.apache.directory.fortress.core.SecurityException se)
                        {
                            String error = "Failed deassign user: " + value + ", SecurityException=" + se;
                            log.warn( error );
                            display.setMessage( error );
                        }
                    }
                }
            };

            addOrReplace( table );
        }

        public String getMemberAssign()
        {
            return memberAssign;
        }

        public void setMemberAssign( String memberAssign )
        {
            this.memberAssign = memberAssign;
        }

        public String getMemberPropsSelection()
        {
            return memberPropsSelection;
        }

        public void setMemberPropsSelection( String memberPropsSelection )
        {
            this.memberPropsSelection = memberPropsSelection;
        }
    }
}